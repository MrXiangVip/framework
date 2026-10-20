package com.packages.kof;

/** Selection -> one timed bout -> result -> selection. All changes run on the UI thread. */
public final class GameSession {
    public enum Screen { SELECT, BATTLE, RESULT }
    public static final String[] NAMES = {"KYO", "RYO", "ROBERT", "TAKUMA", "IORI", "KING", "DAIMON", "CHIZURU", "KENSOU", "BENIMARU"};
    public static final int[] COLORS = {0x21b9c8, 0xeee8dc, 0x3877b2, 0x9cabc1, 0xd53945, 0x8f62be, 0xba7447, 0xe65470, 0x65aa64, 0x608ed7};
    public static final int[] HAIR = {0x29202c, 0xe6b451, 0x172a3c, 0xc6cad3, 0x341e35, 0xe5cf84, 0x202c39, 0x171b2c, 0x86734a, 0xe5e7f1};
    private final FightEngine fight = new FightEngine(System.nanoTime());
    private Screen screen = Screen.SELECT;
    private int selected = 1, opponent = 4;
    private double resultTime;
    public Screen getScreen() { return screen; }
    public int getSelected() { return selected; }
    public int getOpponent() { return opponent; }
    public FightEngine getFight() { return fight; }
    public double getResultTime() { return resultTime; }
    public void select(int index) { if (screen == Screen.SELECT) selected = Math.floorMod(index, NAMES.length); }
    public void start() {
        if (screen != Screen.SELECT) return;
        opponent = Math.floorMod(selected + 3, NAMES.length);
        if (opponent == selected) opponent = (selected + 1) % NAMES.length;
        fight.restart(); fight.setCharacters(selected, opponent);
        screen = Screen.BATTLE;
    }
    public void back() { fight.releaseControls(); screen = Screen.SELECT; resultTime = 0; }
    public void update(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) return;
        if (screen == Screen.SELECT) return;
        if (screen == Screen.RESULT) {
            resultTime -= Math.min(dt, .25);
            if (resultTime <= 0) back();
            return;
        }
        fight.update(dt);
        if (fight.getState() == FightEngine.State.MATCH_OVER) { screen = Screen.RESULT; resultTime = 3.5; fight.releaseControls(); }
    }
}
