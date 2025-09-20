package com.jaceg18.Gameplay.Utility;


import java.util.ArrayList;
import java.util.List;

public final class MoveGen {
    private MoveGen(){}


    public static List<Integer> legalMovesFromSquare(GameState state, int fromSq) {
        List<Integer> legal = new ArrayList<>(32);
        boolean side = state.whiteToMove();

        MoveGen.generate(state, side, m -> {
            if (GameState.from(m) != fromSq) return;
            GameState.Undo u = state.make(m);
            boolean moverIsWhite = !state.whiteToMove();
            boolean ok = !Attacks.isInCheck(state, moverIsWhite);
            state.unmake(u);
            if (ok) legal.add(m);
        });
        return legal;
    }

    public static List<Integer> generateAllLegal(GameState state) {
        List<Integer> legal = new ArrayList<>(64);
        boolean side = state.whiteToMove();
        MoveGen.generate(state, side, m -> {
            GameState.Undo u = state.make(m);
            boolean moverIsWhite = !state.whiteToMove();
            boolean ok = !Attacks.isInCheck(state, moverIsWhite);
            state.unmake(u);
            if (ok) legal.add(m);
        });
        return legal;
    }

    public static void generate(GameState s, boolean white, java.util.function.IntConsumer sink) {
        final long own = white ? s.whitePieces() : s.blackPieces();
        final long opp = white ? s.blackPieces() : s.whitePieces();
        final long occ = s.allPieces();
        final long empty = ~occ; // (optionally) & 0xFFFFFFFFFFFFFFFFL;

        // ----------------
        // PAWNS
        // ----------------
        long pawns = s.pawns(white);

        // Singles
        long singles = white ? ((pawns << 8) & empty)
                : ((pawns >>> 8) & empty);

        // Emit singles (with promotion on last rank)
        long singlesBB = singles;
        while (singlesBB != 0) {
            long toBB = singlesBB & -singlesBB;
            int to = Long.numberOfTrailingZeros(toBB);
            int from = white ? to - 8 : to + 8;

            int toRank = to >>> 3;
            boolean promo = white ? (toRank == 7) : (toRank == 0);
            if (promo) {
                // 0=N,1=B,2=R,3=Q
                sink.accept(GameState.move(from, to, 0, 0, 0));
                sink.accept(GameState.move(from, to, 0, 0, 1));
                sink.accept(GameState.move(from, to, 0, 0, 2));
                sink.accept(GameState.move(from, to, 0, 0, 3));
            } else {
                sink.accept(GameState.move(from, to, 0, 0, -1));
            }
            singlesBB ^= toBB;
        }

        // Doubles (from rank 2/7)
        long doubles;
        if (white) {
            long wpOnStart = pawns & BitUtility.maskForRank(2);
            long oneStep   = (wpOnStart << 8) & empty;
            doubles        = (oneStep << 8) & empty;
        } else {
            long bpOnStart = pawns & BitUtility.maskForRank(7);
            long oneStep   = (bpOnStart >>> 8) & empty;
            doubles        = (oneStep >>> 8) & empty;
        }
        long dblBB = doubles;
        while (dblBB != 0) {
            long toBB = dblBB & -dblBB;
            int to = Long.numberOfTrailingZeros(toBB);
            int from = white ? to - 16 : to + 16;
            sink.accept(GameState.move(from, to, 0, GameState.FLAG_DBL, -1));
            dblBB ^= toBB;
        }

        // Captures (normal)
        long pawnAttacks = white ? BitUtility.whitePawnAttacks(pawns)
                : BitUtility.blackPawnAttacks(pawns);
        long pawnCaps = pawnAttacks & opp;

        long capsBB = pawnCaps;
        while (capsBB != 0) {
            long toBB = capsBB & -capsBB;
            int to = Long.numberOfTrailingZeros(toBB);
            int tf = to & 7; // file

            if (white) {
                // from NW: to-9
                if (tf > 0) {
                    int from = to - 9;
                    if (from >= 0 && ((pawns & (1L << from)) != 0L)) {
                        int toRank = to >>> 3;
                        boolean promo = (toRank == 7);
                        if (promo) {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 0));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 1));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 2));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 3));
                        } else {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, -1));
                        }
                    }
                }
                // from NE: to-7
                if (tf < 7) {
                    int from = to - 7;
                    if (from >= 0 && ((pawns & (1L << from)) != 0L)) {
                        int toRank = to >>> 3;
                        boolean promo = (toRank == 7);
                        if (promo) {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 0));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 1));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 2));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 3));
                        } else {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, -1));
                        }
                    }
                }
            } else {
                // from SW: to+7
                if (tf > 0) {
                    int from = to + 7;
                    if (from < 64 && ((pawns & (1L << from)) != 0L)) {
                        int toRank = to >>> 3;
                        boolean promo = (toRank == 0);
                        if (promo) {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 0));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 1));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 2));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 3));
                        } else {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, -1));
                        }
                    }
                }
                // from SE: to+9
                if (tf < 7) {
                    int from = to + 9;
                    if (from < 64 && ((pawns & (1L << from)) != 0L)) {
                        int toRank = to >>> 3;
                        boolean promo = (toRank == 0);
                        if (promo) {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 0));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 1));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 2));
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, 3));
                        } else {
                            sink.accept(GameState.move(from, to, 0, GameState.FLAG_CAPTURE, -1));
                        }
                    }
                }
            }

            capsBB ^= toBB;
        }

        // En passant
        int ep = s.epSquare();
        if (ep != -1) {
            int tf = ep & 7;
            if (white) {
                if (tf > 0) {
                    int from = ep - 9;
                    if (from >= 0 && ((pawns & (1L << from)) != 0L)) {
                        sink.accept(GameState.move(from, ep, 0, GameState.FLAG_CAPTURE | GameState.FLAG_EP, -1));
                    }
                }
                if (tf < 7) {
                    int from = ep - 7;
                    if (from >= 0 && ((pawns & (1L << from)) != 0L)) {
                        sink.accept(GameState.move(from, ep, 0, GameState.FLAG_CAPTURE | GameState.FLAG_EP, -1));
                    }
                }
            } else {
                if (tf > 0) {
                    int from = ep + 7;
                    if (from < 64 && ((pawns & (1L << from)) != 0L)) {
                        sink.accept(GameState.move(from, ep, 0, GameState.FLAG_CAPTURE | GameState.FLAG_EP, -1));
                    }
                }
                if (tf < 7) {
                    int from = ep + 9;
                    if (from < 64 && ((pawns & (1L << from)) != 0L)) {
                        sink.accept(GameState.move(from, ep, 0, GameState.FLAG_CAPTURE | GameState.FLAG_EP, -1));
                    }
                }
            }
        }

        // ----------------
        // KNIGHTS
        // ----------------
        long knights = s.knights(white);
        long nBB = knights;
        while (nBB != 0) {
            long fromBB = nBB & -nBB;
            int from = Long.numberOfTrailingZeros(fromBB);
            long targets = BitUtility.squaresAKnightCouldAttackFrom(from) & ~own;
            long t = targets;
            while (t != 0) {
                long toBit = t & -t;
                int to = Long.numberOfTrailingZeros(toBit);
                int flags = ((opp & toBit) != 0) ? GameState.FLAG_CAPTURE : 0;
                sink.accept(GameState.move(from, to, 1, flags, -1));
                t ^= toBit;
            }
            nBB ^= fromBB;
        }

        // ----------------
        // BISHOPS
        // ----------------
        long bishops = s.bishops(white);
        long bBB = bishops;
        while (bBB != 0) {
            long fromBB = bBB & -bBB;
            int from = Long.numberOfTrailingZeros(fromBB);
            long targets = BitUtility.squaresABishopCouldSlideTo(from, occ) & ~own;
            long t = targets;
            while (t != 0) {
                long toBit = t & -t;
                int to = Long.numberOfTrailingZeros(toBit);
                int flags = ((opp & toBit) != 0) ? GameState.FLAG_CAPTURE : 0;
                sink.accept(GameState.move(from, to, 2, flags, -1));
                t ^= toBit;
            }
            bBB ^= fromBB;
        }

        // ----------------
        // ROOKS
        // ----------------
        long rooks = s.rooks(white);
        long rBB = rooks;
        while (rBB != 0) {
            long fromBB = rBB & -rBB;
            int from = Long.numberOfTrailingZeros(fromBB);
            long targets = BitUtility.squaresARookCouldSlideTo(from, occ) & ~own;
            long t = targets;
            while (t != 0) {
                long toBit = t & -t;
                int to = Long.numberOfTrailingZeros(toBit);
                int flags = ((opp & toBit) != 0) ? GameState.FLAG_CAPTURE : 0;
                sink.accept(GameState.move(from, to, 3, flags, -1));
                t ^= toBit;
            }
            rBB ^= fromBB;
        }

        // ----------------
        // QUEENS
        // ----------------
        long queens = s.queens(white);
        long qBB = queens;
        while (qBB != 0) {
            long fromBB = qBB & -qBB;
            int from = Long.numberOfTrailingZeros(fromBB);
            long targets = BitUtility.squaresAQueenCouldSlideTo(from, occ) & ~own;
            long t = targets;
            while (t != 0) {
                long toBit = t & -t;
                int to = Long.numberOfTrailingZeros(toBit);
                int flags = ((opp & toBit) != 0) ? GameState.FLAG_CAPTURE : 0;
                sink.accept(GameState.move(from, to, 4, flags, -1));
                t ^= toBit;
            }
            qBB ^= fromBB;
        }

        // ----------------
        // KING (steps + castling)
        // ----------------
        long king = s.king(white);
        if (king != 0) {
            int from = Long.numberOfTrailingZeros(king);
            long kTargets = BitUtility.squaresAKingCouldAttackFrom(from) & ~own;
            long t = kTargets;
            while (t != 0) {
                long toBit = t & -t;
                int to = Long.numberOfTrailingZeros(toBit);
                int flags = ((opp & toBit) != 0) ? GameState.FLAG_CAPTURE : 0;
                sink.accept(GameState.move(from, to, 5, flags, -1));
                t ^= toBit;
            }

            // Castling (through-check safe)
            if (white) {
                int e1 = BitUtility.squareIndexOf("e1");
                int f1 = BitUtility.squareIndexOf("f1");
                int g1 = BitUtility.squareIndexOf("g1");
                int d1 = BitUtility.squareIndexOf("d1");
                int c1 = BitUtility.squareIndexOf("c1");
                int b1 = BitUtility.squareIndexOf("b1");

                // WK-side: right bit 1
                if ((s.castlingRights() & 0b0001) != 0) {
                    boolean pathEmpty = ((occ & (BitUtility.maskFor("f1") | BitUtility.maskFor("g1"))) == 0);
                    if (pathEmpty
                            && !Attacks.isSquareAttackedBy(s, e1, false)
                            && !Attacks.isSquareAttackedBy(s, f1, false)
                            && !Attacks.isSquareAttackedBy(s, g1, false)) {
                        sink.accept(GameState.move(e1, g1, 5, GameState.FLAG_CASTLE, -1));
                    }
                }
                // WQ-side: right bit 2
                if ((s.castlingRights() & 0b0010) != 0) {
                    boolean pathEmpty = ((occ & (BitUtility.maskFor("d1") | BitUtility.maskFor("c1") | BitUtility.maskFor("b1"))) == 0);
                    if (pathEmpty
                            && !Attacks.isSquareAttackedBy(s, e1, false)
                            && !Attacks.isSquareAttackedBy(s, d1, false)
                            && !Attacks.isSquareAttackedBy(s, c1, false)) {
                        sink.accept(GameState.move(e1, c1, 5, GameState.FLAG_CASTLE, -1));
                    }
                }
            } else {
                int e8 = BitUtility.squareIndexOf("e8");
                int f8 = BitUtility.squareIndexOf("f8");
                int g8 = BitUtility.squareIndexOf("g8");
                int d8 = BitUtility.squareIndexOf("d8");
                int c8 = BitUtility.squareIndexOf("c8");
                int b8 = BitUtility.squareIndexOf("b8");

                // BK-side: right bit 4
                if ((s.castlingRights() & 0b0100) != 0) {
                    boolean pathEmpty = ((occ & (BitUtility.maskFor("f8") | BitUtility.maskFor("g8"))) == 0);
                    if (pathEmpty
                            && !Attacks.isSquareAttackedBy(s, e8, true)
                            && !Attacks.isSquareAttackedBy(s, f8, true)
                            && !Attacks.isSquareAttackedBy(s, g8, true)) {
                        sink.accept(GameState.move(e8, g8, 5, GameState.FLAG_CASTLE, -1));
                    }
                }
                // BQ-side: right bit 8
                if ((s.castlingRights() & 0b1000) != 0) {
                    boolean pathEmpty = ((occ & (BitUtility.maskFor("d8") | BitUtility.maskFor("c8") | BitUtility.maskFor("b8"))) == 0);
                    if (pathEmpty
                            && !Attacks.isSquareAttackedBy(s, e8, true)
                            && !Attacks.isSquareAttackedBy(s, d8, true)
                            && !Attacks.isSquareAttackedBy(s, c8, true)) {
                        sink.accept(GameState.move(e8, c8, 5, GameState.FLAG_CASTLE, -1));
                    }
                }
            }
        }
    }

}
