package com.chess;

import java.util.*;

/**
 *
 */
public class Chessboard{

    final private String[] boardSetup;
    final private String[] pieceSetup;
    final public int[] size;

    private Map<Coord, Piece> piecePosition = new HashMap<>();
    private playerColor turnColor;
    private CastlingTracker castling;
    Coord enPassantSquare;
    boolean isEnPassant = false;
    private int halfMoveClock;
    private int fullMoveNumber;



    char promoteToPiece = 'q';

    private EnumMap<playerColor, ChessCheckTracker> checkTrackers = new EnumMap<>(playerColor.class);
    //TODO: private EnumMap<playerColor, Piece> kings = new EnumMap<>(playerColor.class);
    List<LineOfSight> currPins = new ArrayList<>();
    List<LineOfSight> currChecks = new ArrayList<>();

    /**
     * Generates a Chessboard from the boardSetup String.
     * @param   FEN     FEN String to be loaded. Can accept Non 8x8 board sizes.
     */
    public Chessboard(String FEN){
        boardSetup = FEN.split(" ", 0);
        pieceSetup = boardSetup[0].split("/", 0);

        try{
            size = calculateBoardSize();
        }catch (InvalidFENException e){
            e.printStackTrace();
            throw new AssertionError(e.getMessage());
        }

        try{
            populateBoard();
        }catch (InvalidFENException e){
            e.printStackTrace();
            throw new AssertionError(e.getMessage());
        }

        setupBoardMisc();

    }

    /**
     * Generates a Classic 8x8 Chessboard from the preset boardSetup String.
     * <p>
     * See also {@link #Chessboard(String)}
     * </p>
     */
    public Chessboard(){
        // Constructor for the classical 8x8 Chess Board
        String FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        boardSetup = FEN.split(" ", 0);
        pieceSetup = boardSetup[0].split("/", 0);

        try{
            size = calculateBoardSize();
            populateBoard();
        }catch (InvalidFENException e){
            e.printStackTrace();
            throw new AssertionError(e.getMessage());
        }

        setupBoardMisc();
    }

    /**
     * <p>
     * Calculates the shape/size of the board that will be generated by {@link #populateBoard()},
     * and assigns it to boardSize.
     * Also checks if the board has a rectangular shape.
     * </p>
     * @throws InvalidFENException   if boardSetup will not generate a rectangular board
     */
    private int[] calculateBoardSize()
            throws InvalidFENException {

        int[] boardWidth = new int[pieceSetup.length];

        // Calculate boardWidth (row length)
        for (int i = 0; i < pieceSetup.length; i++){
            for (char c: pieceSetup[i].toCharArray()) {
                if (Character.isDigit(c)) boardWidth[i] += (c - '0');
                else boardWidth[i]++;
            }
        }

        // check if generated board widths are consistent
        for (int width: boardWidth) {
            if(width != boardWidth[0])
                throw new InvalidFENException("Board shape generated by boardSetup is not rectangular.");
        }

        return new int[]{boardWidth[0], pieceSetup.length};
    }

    /**
     * Generates and assigns boardState from boardSetup.
     */
    private void populateBoard()
            throws InvalidFENException {

        Coord currCoord = new Coord(0, size[1] - 1);

        for (String row: pieceSetup) {
            for (char tileState : row.toCharArray()) {

                // If tileState is an alphabet, then generate corresponding Piece
                if(Character.isAlphabetic(tileState)) {
                    piecePosition.put(currCoord, PieceFactory.newPiece(this, tileState, currCoord));
                    currCoord = currCoord.add(1, 0);
                }

                // If tileState is number, then advance currCoord by (int)tileState amount
                else currCoord = currCoord.add(tileState - '0', 0);
            }
            currCoord = new Coord(0, currCoord.y - 1);

        }
    }

    /**
     * Sets up the turnColor, castling, enPassantSquare, halfMoveClock and fullMoveNumber
     */
    private void setupBoardMisc(){
        turnColor = (boardSetup[1].equals("w")) ? playerColor.White: playerColor.Black;

        castling = new CastlingTracker(this, boardSetup[2]);

        if(!boardSetup[3].equals("-")){
            char[] coordSetup = boardSetup[3].toCharArray();

            enPassantSquare = new Coord(
                    Character.toLowerCase(coordSetup[0]) - 'a',
                    coordSetup[1] - '1'
            );
        }else enPassantSquare = null;

        halfMoveClock = Integer.parseInt(boardSetup[4]);
        fullMoveNumber = Integer.parseInt(boardSetup[5]);

        setupCheckTrackers();
        updatePinsAndChecks();
    }

    private void setupCheckTrackers(){
        checkTrackers.put(currColor(), new ChessCheckTracker(this));
        switchTurns();
        checkTrackers.put(currColor(), new ChessCheckTracker(this));
        switchTurns();
        fullMoveNumber --;
    }

    /**
     * Returns Piece obj located at (x, y), from this.state
     * @param x     x Coordinate
     * @param y     y Coordinate
     * @return      Piece located at (x,y)
     */
    public Piece pieceAt(int x, int y){
        return piecePosition.get(new Coord(x, y));
    }

    public Piece pieceAt(Coord coord){
        return piecePosition.get(coord);
    }


    public boolean hasPieceAt(Coord coord){
        return piecePosition.get(coord) != null && !(piecePosition.get(coord) instanceof Edge);
    }

    public boolean hasPieceAt(int x, int y){
        return hasPieceAt(new Coord(x, y));
    }

    /**
     * @param coord     Coord to see if it is in the board
     * @return          true if coord is within boardSize, and is not an Edge Piece
     */
    public boolean coordInBoard(Coord coord){
        return !(piecePosition.get(coord) instanceof Edge)
                && size[0] > coord.x && coord.x >= 0
                && size[1] > coord.y && coord.y >= 0;
    }

    public boolean coordInBoard(int x, int y){
        return coordInBoard(new Coord(x, y));
    }

    /**
     * Automatically edits the board according to the parameter. <br>
     * Inputs are <b>assumed to be a legal move. </b><br>
     * Updates turnColor, halfMoveClock and fullMoveCounter. <br>
     * Handles assigning enPassantSquare. <br>
     * @param move      a ChessTurn object, contains moveFrom and moveTo coords.
     */
    void movePiece(ChessTurn move){
        if(!hasPieceAt(move.from) || !coordInBoard(move.from) || !coordInBoard(move.to)){
            throw new AssertionError("Move is on an empty square, or is not in the board.");
        }

        updateHalfMoveClock(move);
        updatePiecePositions(move);
        castling.update();
        updateEnPassantSquare(move);
        switchTurns();

        System.out.println("Cb.mP: Piece Moved");
        System.out.println("Cb.mP; currColor: " + currColor());

        // Update checks for next player
        currCheckTracker().update(move, isEnPassant);
        updatePinsAndChecks();
    }

    private void updateHalfMoveClock(ChessTurn move){
        if (hasPieceAt(move.to) || pieceAt(move.from) instanceof Pawn) halfMoveClock = 0;
        else halfMoveClock++;
    }

    private void updatePiecePositions(ChessTurn move){
        isEnPassant = false;
        Piece pieceToMove = piecePosition.get(move.from);
        pieceToMove.movePiece(move);
    }

    void placePiece(Coord moveTo, Piece movedPiece){
        piecePosition.put(moveTo, movedPiece);
    }

    void removePiece(Coord coord){
        piecePosition.remove(coord);
    }

    private void updateEnPassantSquare(ChessTurn move){
        if(pieceAt(move.to) instanceof Pawn pawnToMove){
            Coord primaryMoveDirection = pawnToMove.primaryMoveDirection();
            if (move.movedBy().equals(primaryMoveDirection.multiply(2)) &&
                    (hasNeighboringEnemyPawn(move.to))) {
                enPassantSquare = move.from.add(primaryMoveDirection);
            }else enPassantSquare = null;
        }else enPassantSquare = null;
    }

    private void updatePinsAndChecks(){
        List<List<LineOfSight>> pinsAndChecks = currCheckTracker().pinsAndChecks();
        currPins = pinsAndChecks.get(0);
        currChecks = pinsAndChecks.get(1);
    }

    private boolean hasEnemyPawn(Coord coord){
        if(isEnemyPiece(coord)) return pieceAt(coord) instanceof Pawn;
        return false;
    }

    private boolean hasNeighboringEnemyPawn(Coord coord){
        return hasEnemyPawn(coord.add(1,0)) || hasEnemyPawn(coord.add(-1,0));
    }

    private void switchTurns(){
        if(turnColor == playerColor.White) turnColor = playerColor.Black;
        else{
            turnColor = playerColor.White;
            fullMoveNumber ++;
        }
    }

    void promotePawn(Pawn pawnToPromote){
        removePiece(pawnToPromote.currCoord);
        if(currColor().equals(playerColor.White)) promoteToPiece = Character.toUpperCase(promoteToPiece);
        else promoteToPiece = Character.toLowerCase(promoteToPiece);

        placePiece(
                pawnToPromote.currCoord,
                PieceFactory.newPiece(this, promoteToPiece, pawnToPromote.currCoord)
        );
        promoteToPiece = 'q';
    }

    ChessCheckTracker currCheckTracker(){
        return checkTrackers.get(currColor());
    }

    public Map<Coord, Piece> getBoard(){
        Map<Coord, Piece> boardState = new HashMap<>();
        for (Coord coord: piecePosition.keySet()) {
            boardState.put(new Coord(coord.x, coord.y), this.piecePosition.get(coord).clone());
        }
        return boardState;
    }

    /**
     * @param coord     coordinate to check the piece color of
     * @return          True if piece located at coord is the same color with the player who currently has the turn
     */
    boolean isAlliedPiece(Coord coord){
        if (hasPieceAt(coord)) return pieceAt(coord).color() == turnColor;
        return false;
    }

    /**
     * @param coord     coordinate to check the piece color of
     * @return          True if piece located at coord is the same color with the player who currently has the turn
     */
    boolean isEnemyPiece(Coord coord){
        if (hasPieceAt(coord)) return pieceAt(coord).color() != turnColor;
        return false;
    }

    public playerColor currColor(){
        return turnColor;
    }

    Set<Coord> getCastlingRights(King king){
        return castling.validCastleCoords(king);
    }

    boolean isEnPassantSquare(Coord coord){
        if(enPassantSquare != null) return enPassantSquare.equals(coord);
        else return false;
    }

    int getHalfMoveClock(){
        return halfMoveClock;
    }

    int getFullMoveNumber(){
        return fullMoveNumber;
    }

    public String FEN(){

        StringBuilder FENString = new StringBuilder();

        char[][] pieceArray = new char[size[0]][size[1]];

        for (Coord coord: piecePosition.keySet()) {
            pieceArray[size[1] - 1 - coord.y][coord.x] = piecePosition.get(coord).FENChar();
        }

        // Piece part of FEN
        StringBuilder strRepresentation = new StringBuilder();
        for (char[] row : pieceArray) {
            int i = 0;
            for(char tile : row){

                if(tile == '\u0000') i++;
                else{
                    if(i > 0) {
                        strRepresentation.append(i);
                        i = 0;
                    }
                    strRepresentation.append(tile);
                }
            }
            if(i > 0) strRepresentation.append(i);
            strRepresentation.append("/");
        }
        FENString.append(strRepresentation.substring(0, strRepresentation.length() - 1));
        FENString.append(" ");


        // Turn color of FEN
        if(turnColor == playerColor.White) FENString.append("w");
        else FENString.append("b");
        FENString.append(" ");

        FENString.append(castling.toString());
        FENString.append(" ");

        // enPassantSquare part of FEN
        if(enPassantSquare == null) FENString.append("-");
        else FENString.append((char)(enPassantSquare.x + 'a') ).append((char)(enPassantSquare.y + '0'));
        FENString.append(" ");

        FENString.append(halfMoveClock);
        FENString.append(" ");

        FENString.append(fullMoveNumber);
        FENString.append(" ");

        return FENString.toString();
    }

    /** @return  piece placement part of FEN of current boardState. Also affects System.out.print */
    @Override
    public String toString() {
        return getClass().getName() + " " + FEN();
    }

    boolean isKingChecked(){
        System.out.println(currChecks);
        return !currChecks.isEmpty();
    }

    boolean isCoordAttacked(Coord coord){
        return currCheckTracker().isCoordAttacked(coord);
    }

}

