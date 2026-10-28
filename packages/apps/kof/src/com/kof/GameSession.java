package com.packages.kof;

/** Home -> character selection -> bout -> result. All mutations run on the UI thread. */
public final class GameSession {
    public enum Screen { SELECT, ROSTER, BATTLE, RESULT }
    public enum Mode {
        TRAINING("单人训练", "TRAINING", "单人练习 · 不计时 · 熟悉技能"),
        CPU("人机对战", "VS COMPUTER", "60 秒决胜 · 挑战电脑对手"),
        VERSUS("双人对战", "LOCAL VERSUS", "同屏竞技 · 双人键盘操作");
        public final String title, english, description;
        Mode(String title, String english, String description) { this.title=title; this.english=english; this.description=description; }
    }
    public static final int SUNWUKONG = 0, ZHUBAJIE = 1;
    public static final String[] NAMES = {"孙悟空", "猪八戒"};
    public static final String[] SPRITE_DIRECTORIES = {"sunwukong", "zhubajie"};
    public int getRosterSize() { return NAMES.length; }
    private final FightEngine fight = new FightEngine(System.nanoTime());
    private Screen screen = Screen.SELECT;
    private Mode mode = Mode.CPU;
    private int selected = 0, opponent = ZHUBAJIE, stage, selectingPlayer;
    private double resultTime;
    public Screen getScreen() { return screen; }
    public Mode getMode() { return mode; }
    public int getStage() { return stage; }
    public int getSelectingPlayer() { return selectingPlayer; }
    public int getSelected() { return selected; }
    public int getOpponent() { return opponent; }
    public FightEngine getFight() { return fight; }
    public double getResultTime() { return resultTime; }
    public void setMode(Mode value) { if(screen==Screen.SELECT) { mode=java.util.Objects.requireNonNull(value); if(selected>=getRosterSize()) selected=0; } }
    public void selectStage(int index) { if(screen==Screen.SELECT) stage=Math.floorMod(index,StageArt.NAMES.length); }
    public void openRoster() { if(screen==Screen.SELECT) { screen=Screen.ROSTER; selectingPlayer=0; } }
    public void select(int index) {
        if(screen!=Screen.ROSTER) return;
        if(selectingPlayer==0) selected=Math.floorMod(index,getRosterSize());
        else opponent=Math.floorMod(index,getRosterSize());
    }
    public void start() {
        if(screen!=Screen.ROSTER) return;
        if(mode==Mode.VERSUS && selectingPlayer==0) { selectingPlayer=1; return; }
        if(mode==Mode.CPU) opponent=1-selected;
        fight.setComputerEnabled(mode==Mode.CPU); fight.setTraining(mode==Mode.TRAINING);
        fight.setCharacters(selected,opponent); fight.restart(); screen=Screen.BATTLE;
    }
    public void back() { fight.releaseControls(); screen=Screen.SELECT; resultTime=0; selectingPlayer=0; }
    public void update(double dt) {
        if(!Double.isFinite(dt)||dt<=0) return;
        if(screen==Screen.SELECT||screen==Screen.ROSTER) return;
        if(screen==Screen.RESULT) { resultTime-=Math.min(dt,.25); if(resultTime<=0) back(); return; }
        fight.update(dt);
        if(fight.getState()==FightEngine.State.MATCH_OVER) { screen=Screen.RESULT; resultTime=3.5; fight.releaseControls(); }
    }
}
