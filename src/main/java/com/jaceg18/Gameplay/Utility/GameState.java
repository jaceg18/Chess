package com.jaceg18.Gameplay.Utility;


public final class GameState {

    // ---------- side / rights / clocks ----------
    private boolean whiteToMove = true;
    // castling rights bitmask: 1=WK, 2=WQ, 4=BK, 8=BQ
    private int castlingRights = 0b1111;
    // en-passant target square (0..63) or -1 if none
    private int epSquare = -1;
    private int halfmoveClock = 0;
    public int fullmoveNumber = 1;

    // ---------- per-piece bitboards (colored) ----------
    // White
    private long WP, WN, WB, WR, WQ, WK;
    // Black
    private long BP, BN, BB, BR, BQ, BK;

    // ---------- cached aggregates ----------
    private long whitePieces, blackPieces, allPieces;

    // ---------- move encoding ----------
    // bits: 0..5 to, 6..11 from, 12..14 promo(0=N,1=B,2=R,3=Q), 15 cap, 16 ep, 17 castle, 18 dblPush, 19..21 mover(0=P,1=N,2=B,3=R,4=Q,5=K)
    public static final int FLAG_CAPTURE = 1<<15;
    public static final int FLAG_EP      = 1<<16;
    public static final int FLAG_CASTLE  = 1<<17;
    public static final int FLAG_DBL     = 1<<18;

    public static int move(int from,int to,int moverKind,int flags,int promo /*-1 or 0..3*/){
        int m = to | (from<<6) | (moverKind<<19) | flags;
        if (promo >= 0) m |= ((promo + 1) << 12);   // store 1..4 ; 0 means "no promo"
        return m;
    }

    public static int promoKind(int m){
        int k = (m >>> 12) & 7;                     // 0=no promo, 1..4 = N,B,R,Q
        return (k == 0) ? -1 : (k - 1);             // back to 0..3 or -1
    }

    public static boolean isPromotion(int m){ return promoKind(m) >= 0; }
    public static int from(int m){ return (m>>>6)&63; }
    public static int to(int m){ return m&63; }
    public static int moverKind(int m){ return (m>>>19)&7; }  // 0=P,1=N,2=B,3=R,4=Q,5=K
    public static boolean isCapture(int m){ return (m & FLAG_CAPTURE)!=0; }
    public static boolean isEP(int m){ return (m & FLAG_EP)!=0; }
    public static boolean isCastle(int m){ return (m & FLAG_CASTLE)!=0; }
    public static boolean isDoublePawn(int m){ return (m & FLAG_DBL)!=0; }

    // ---------- undo record ----------
// ---------- undo record (REPLACE your current one) ----------
    // ---------- undo record (mutable + both APIs) ----------
    public static final class Undo {
        int move;
        public int castlingRights;
        public int epSquare;
        int halfmoveClock;
        boolean whiteToMove;
        long WP,WN,WB,WR,WQ,WK,BP,BN,BB,BR,BQ,BK;

        public Undo() {}


        // keep old constructor so code like `new Undo(m, this)` compiles
        public Undo(int move, GameState s) { captureFrom(s, move); }

        void captureFrom(GameState s, int move){
            this.move = move;
            this.castlingRights = s.castlingRights;
            this.epSquare = s.epSquare;
            this.halfmoveClock = s.halfmoveClock;
            this.whiteToMove = s.whiteToMove;
            this.WP=s.WP; this.WN=s.WN; this.WB=s.WB; this.WR=s.WR; this.WQ=s.WQ; this.WK=s.WK;
            this.BP=s.BP; this.BN=s.BN; this.BB=s.BB; this.BR=s.BR; this.BQ=s.BQ; this.BK=s.BK;
        }
    }


    // ---------- make (non-allocating, for search) ----------
    public void makeInPlace(int m, Undo u){
        u.captureFrom(this, m);   // snapshot without allocating a new Undo
        final boolean white = whiteToMove;
        final int f = from(m), t = to(m);
        long fromMask = 1L<<f, toMask = 1L<<t;

        epSquare = -1;

        switch (moverKind(m)) {
            case 0 -> {
                if (white) { WP ^= fromMask ^ toMask; }
                else       { BP ^= fromMask ^ toMask; }
                if (isDoublePawn(m)) { epSquare = white ? (f+8) : (f-8); }
                if (isEP(m)) {
                    int capSq = white ? (t-8) : (t+8);
                    long capMask = 1L<<capSq;
                    if (white) BP &= ~capMask; else WP &= ~capMask;
                    halfmoveClock = 0;
                } else if (isCapture(m)) {
                    captureAtSquare(t, !white); halfmoveClock = 0;
                } else {
                    halfmoveClock = 0;
                }
                int promo = promoKind(m);
                if (promo >= 0) {
                    if (white) WP &= ~toMask; else BP &= ~toMask;
                    promoteAtSquare(t, white, promo);
                }
            }
            case 1 -> { if (white) WN ^= fromMask ^ toMask; else BN ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 2 -> { if (white) WB ^= fromMask ^ toMask; else BB ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 3 -> { if (white) WR ^= fromMask ^ toMask; else BR ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 4 -> { if (white) WQ ^= fromMask ^ toMask; else BQ ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 5 -> {
                if (white) { WK ^= fromMask ^ toMask; castlingRights &= ~(1|2); }
                else       { BK ^= fromMask ^ toMask; castlingRights &= ~(4|8); }
                if (isCapture(m)) { captureAtSquare(t, !white); halfmoveClock = 0; }
                if (isCastle(m)) {
                    int ff = f & 7, tt = t & 7;
                    boolean kingSide = (tt > ff);
                    if (white) {
                        if (kingSide) { WR ^= (BitUtility.maskFor("h1") ^ BitUtility.maskFor("f1")); }
                        else          { WR ^= (BitUtility.maskFor("a1") ^ BitUtility.maskFor("d1")); }
                    } else {
                        if (kingSide) { BR ^= (BitUtility.maskFor("h8") ^ BitUtility.maskFor("f8")); }
                        else          { BR ^= (BitUtility.maskFor("a8") ^ BitUtility.maskFor("d8")); }
                    }
                    halfmoveClock++;
                } else {
                    halfmoveClock++;
                }
            }
        }

        updateCastlingRightsOnRookMoveOrCapture(f, t);
        whiteToMove = !whiteToMove;
        if (!whiteToMove) fullmoveNumber++;
        recomputeAggregates();
    }


    // ---------- construction ----------
    public GameState() { setToStartpos(); }

    public GameState(GameState o){
        this.WP=o.WP; this.WN=o.WN; this.WB=o.WB; this.WR=o.WR; this.WQ=o.WQ; this.WK=o.WK;
        this.BP=o.BP; this.BN=o.BN; this.BB=o.BB; this.BR=o.BR; this.BQ=o.BQ; this.BK=o.BK;

        this.whitePieces=o.whitePieces; this.blackPieces=o.blackPieces; this.allPieces=o.allPieces;

        this.whiteToMove=o.whiteToMove;
        this.castlingRights=o.castlingRights;
        this.epSquare=o.epSquare;
        this.halfmoveClock=o.halfmoveClock;
        this.fullmoveNumber=o.fullmoveNumber;
    }

    public GameState copy(){ return new GameState(this); }

    public void setToStartpos() {
        WP = BitUtility.maskForRank(2);
        WN = BitUtility.maskFor("b1") | BitUtility.maskFor("g1");
        WB = BitUtility.maskFor("c1") | BitUtility.maskFor("f1");
        WR = BitUtility.maskFor("a1") | BitUtility.maskFor("h1");
        WQ = BitUtility.maskFor("d1");
        WK = BitUtility.maskFor("e1");

        BP = BitUtility.maskForRank(7);
        BN = BitUtility.maskFor("b8") | BitUtility.maskFor("g8");
        BB = BitUtility.maskFor("c8") | BitUtility.maskFor("f8");
        BR = BitUtility.maskFor("a8") | BitUtility.maskFor("h8");
        BQ = BitUtility.maskFor("d8");
        BK = BitUtility.maskFor("e8");

        whiteToMove = true;
        castlingRights = 0b1111;
        epSquare = -1;
        halfmoveClock = 0;
        fullmoveNumber = 1;

        recomputeAggregates();
    }

    // ---------- queries ----------
    public boolean whiteToMove(){ return whiteToMove; }
    public int epSquare(){ return epSquare; }
    public int castlingRights(){ return castlingRights; }
    public long whitePieces(){ return whitePieces; }
    public long blackPieces(){ return blackPieces; }
    public long allPieces(){ return allPieces; }

    public long pawns(boolean white){ return white ? WP : BP; }
    public long knights(boolean white){ return white ? WN : BN; }
    public long bishops(boolean white){ return white ? WB : BB; }
    public long rooks(boolean white){ return white ? WR : BR; }
    public long queens(boolean white){ return white ? WQ : BQ; }
    public long king(boolean white){ return white ? WK : BK; }

    // ---------- make / unmake ----------
    // ---------- make (returns an Undo snapshot, like before) ----------
    public Undo make(int m){
        Undo u = new Undo(m, this);   // works again

        final boolean white = whiteToMove;
        final int f = from(m), t = to(m);
        long fromMask = 1L<<f, toMask = 1L<<t;

        // clear ep by default
        epSquare = -1;

        // mover piece switch (by kind) — same logic you already had
        switch (moverKind(m)) {
            case 0 -> { // pawn
                if (white) { WP ^= fromMask ^ toMask; }
                else       { BP ^= fromMask ^ toMask; }

                if (isDoublePawn(m)) { epSquare = white ? (f+8) : (f-8); }

                if (isEP(m)) {
                    int capSq = white ? (t-8) : (t+8);
                    long capMask = 1L<<capSq;
                    if (white) BP &= ~capMask; else WP &= ~capMask;
                    halfmoveClock = 0;
                } else if (isCapture(m)) {
                    captureAtSquare(t, !white);
                    halfmoveClock = 0;
                } else {
                    halfmoveClock = 0; // pawn move resets
                }

                int promo = promoKind(m);
                if (promo >= 0) {
                    // remove pawn at 't' and place promoted piece
                    if (white) WP &= ~toMask; else BP &= ~toMask;
                    promoteAtSquare(t, white, promo); // 0=N,1=B,2=R,3=Q
                }
            }
            case 1 -> { if (white) WN ^= fromMask ^ toMask; else BN ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 2 -> { if (white) WB ^= fromMask ^ toMask; else BB ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 3 -> { if (white) WR ^= fromMask ^ toMask; else BR ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 4 -> { if (white) WQ ^= fromMask ^ toMask; else BQ ^= fromMask ^ toMask; resetHMIfCapture(m, white, t); }
            case 5 -> {
                if (white) {
                    WK ^= fromMask ^ toMask;
                    // lose white castling rights
                    castlingRights &= ~(1|2);
                } else {
                    BK ^= fromMask ^ toMask;
                    castlingRights &= ~(4|8);
                }
                if (isCapture(m)) { captureAtSquare(t, !white); halfmoveClock = 0; }
                // castling rook move
                if (isCastle(m)) {
                    int ff = f & 7, tt = t & 7;
                    boolean kingSide = (tt > ff);
                    if (white) {
                        if (kingSide) { // e1->g1; rook h1->f1
                            WR ^= (BitUtility.maskFor("h1") ^ BitUtility.maskFor("f1"));
                        } else {        // e1->c1; rook a1->d1
                            WR ^= (BitUtility.maskFor("a1") ^ BitUtility.maskFor("d1"));
                        }
                    } else {
                        if (kingSide) { // e8->g8; rook h8->f8
                            BR ^= (BitUtility.maskFor("h8") ^ BitUtility.maskFor("f8"));
                        } else {        // e8->c8; rook a8->d8
                            BR ^= (BitUtility.maskFor("a8") ^ BitUtility.maskFor("d8"));
                        }
                    }
                    halfmoveClock++; // castle is a non-capture non-pawn move
                } else {
                    halfmoveClock++; // non-capture, non-pawn
                }
            }
        }

        // update castling rights if a rook moved or was captured from its initial square
        updateCastlingRightsOnRookMoveOrCapture(f, t);

        // flip side, move number
        whiteToMove = !whiteToMove;
        if (!whiteToMove) fullmoveNumber++;

        recomputeAggregates();
        return u;
    }


    public void unmake(Undo u){
        // restore exact prior snapshot
        this.castlingRights = u.castlingRights;
        this.epSquare = u.epSquare;
        this.halfmoveClock = u.halfmoveClock;
        this.whiteToMove = u.whiteToMove;

        this.WP=u.WP; this.WN=u.WN; this.WB=u.WB; this.WR=u.WR; this.WQ=u.WQ; this.WK=u.WK;
        this.BP=u.BP; this.BN=u.BN; this.BB=u.BB; this.BR=u.BR; this.BQ=u.BQ; this.BK=u.BK;

        recomputeAggregates();
    }

    // ---------- helpers ----------
    private void recomputeAggregates() {
        whitePieces = WP | WN | WB | WR | WQ | WK;
        blackPieces = BP | BN | BB | BR | BQ | BK;
        allPieces   = whitePieces | blackPieces;
    }

    private void captureAtSquare(int sq, boolean whiteCaptured) {
        long m = 1L<<sq;
        if (whiteCaptured) {
            // remove a white piece at sq
            if ((WP & m)!=0) { WP &= ~m; castlingRights &= updateRightsByCapture("white", sq); return; }
            if ((WN & m)!=0) { WN &= ~m; return; }
            if ((WB & m)!=0) { WB &= ~m; return; }
            if ((WR & m)!=0) { WR &= ~m; castlingRights &= updateRightsByCapture("white", sq); return; }
            if ((WQ & m)!=0) { WQ &= ~m; return; }
            if ((WK & m)!=0) { WK &= ~m;
            }
        } else {
            if ((BP & m)!=0) { BP &= ~m; castlingRights &= updateRightsByCapture("black", sq); return; }
            if ((BN & m)!=0) { BN &= ~m; return; }
            if ((BB & m)!=0) { BB &= ~m; return; }
            if ((BR & m)!=0) { BR &= ~m; castlingRights &= updateRightsByCapture("black", sq); return; }
            if ((BQ & m)!=0) { BQ &= ~m; return; }
            if ((BK & m)!=0) { BK &= ~m;
            }
        }
    }


    public int halfmoveClock(){ return halfmoveClock; }  // add this tiny getter

    private void resetHMIfCapture(int m, boolean whiteMover, int toSq){
        if (isCapture(m)) { captureAtSquare(toSq, !whiteMover); halfmoveClock = 0; }
        else halfmoveClock++;
    }

    private void promoteAtSquare(int sq, boolean white, int promo) {
        long m = 1L<<sq;
        // promo: 0=N,1=B,2=R,3=Q
        if (white) {
            switch (promo) {
                case 0 -> WN |= m;
                case 1 -> WB |= m;
                case 2 -> WR |= m;
                default -> WQ |= m;
            }
        } else {
            switch (promo) {
                case 0 -> BN |= m;
                case 1 -> BB |= m;
                case 2 -> BR |= m;
                default -> BQ |= m;
            }
        }
    }

    // clear rights if a rook moves/captured from its start square
    private void updateCastlingRightsOnRookMoveOrCapture(int fromSq, int toSq){
        // a1 (white Q-side rook)
        if (fromSq == BitUtility.squareIndexOf("a1") || toSq == BitUtility.squareIndexOf("a1"))
            castlingRights &= ~0b0010;
        if (fromSq == BitUtility.squareIndexOf("h1") || toSq == BitUtility.squareIndexOf("h1"))
            castlingRights &= ~0b0001;
        if (fromSq == BitUtility.squareIndexOf("a8") || toSq == BitUtility.squareIndexOf("a8"))
            castlingRights &= ~0b1000;
        if (fromSq == BitUtility.squareIndexOf("h8") || toSq == BitUtility.squareIndexOf("h8"))
            castlingRights &= ~0b0100;
    }

    private int updateRightsByCapture(String color, int sq){
        int a1 = BitUtility.squareIndexOf("a1"), h1 = BitUtility.squareIndexOf("h1"),
                a8 = BitUtility.squareIndexOf("a8"), h8 = BitUtility.squareIndexOf("h8");
        int rights = 0b1111;
        if (color.equals("white")) {
            if (sq == a1) rights &= ~0b0010;
            if (sq == h1) rights &= ~0b0001;
        } else {
            if (sq == a8) rights &= ~0b1000;
            if (sq == h8) rights &= ~0b0100;
        }
        return rights;
    }
}
