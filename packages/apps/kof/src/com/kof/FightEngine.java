package com.packages.kof;

import java.util.Random;

/** Deterministic 60 Hz combat simulation, independent of the window and rendering. */
public final class FightEngine {
    public enum Sound { ROUND, FIGHT, PUNCH, KICK, SPECIAL, HIT, BLOCK, JUMP, LAND, KO, TIME_UP, WIN, LOSE, DRAW, SELECT }
    private java.util.function.Consumer<Sound> soundListener = sound -> {};
    public void setSoundListener(java.util.function.Consumer<Sound> listener) { soundListener = java.util.Objects.requireNonNull(listener); }
    private void sound(Sound sound) { soundListener.accept(sound); }
    public enum State { READY, FIGHT, ROUND_OVER, MATCH_OVER }
    public enum Move {
        PUNCH(14, 112, .12, .11, .30), KICK(18, 140, .18, .12, .36),
        LIGHT_PUNCH(6, 90, .04, .08, .15), LIGHT_KICK(8, 112, .07, .09, .19),
        SPECIAL(26, 185, .22, .16, .40);
        public boolean isKick() { return this == KICK || this == LIGHT_KICK; }
        public boolean isLight() { return this == LIGHT_PUNCH || this == LIGHT_KICK; }
        public String label() { switch(this) { case PUNCH: return "重拳"; case KICK: return "重脚"; case LIGHT_PUNCH: return "轻拳"; case LIGHT_KICK: return "轻脚"; default: return "必杀技"; } }
        final int damage, reach;
        final double windup, active, recovery;
        Move(int damage, int reach, double windup, double active, double recovery) {
            this.damage = damage; this.reach = reach; this.windup = windup; this.active = active; this.recovery = recovery;
        }
    }
    public static final class Fighter {
        double x, height, velocity, stun, attackTime, flash;
        int health = 100, energy = 40, facing, attackFacing, wins;
        boolean guard, crouching, connected;
        Move move;
        Fighter(double x, int facing) { this.x = x; this.facing = facing; }
        public double getX() { return x; }
        public double getHeight() { return height; }
        public int getHealth() { return health; }
        public int getEnergy() { return energy; }
        public int getWins() { return wins; }
        public int getFacing() { return move == null ? facing : attackFacing; }
        public boolean isGuarding() { return guard; }
        public boolean isCrouching() { return crouching; }
        public boolean isHit() { return flash > 0; }
        public Move getMove() { return move; }
        public double getAttackTime() { return attackTime; }
    }

    final Fighter player = new Fighter(210, 1), enemy = new Fighter(510, -1);
    private final Random random;
    private State state;
    private double accumulator, stateTime, remaining, aiDecision, aiGuard;
    private int direction, enemyDirection, round, winner;
    private boolean crouchRequested, enemyCrouchRequested, training, paused, computer = true;
    private String message;
    private int playerCharacter, enemyCharacter;
    public void setCharacters(int player, int enemy) { playerCharacter = player; enemyCharacter = enemy; }

    public FightEngine() { this(2026); }
    public FightEngine(long seed) { random = new Random(seed); restart(); }
    public Fighter getPlayer() { return player; }
    public Fighter getEnemy() { return enemy; }
    public State getState() { return state; }
    public int getRound() { return round; }
    public int getWinner() { return winner; }
    public int getSeconds() { return training ? 0 : Math.max(0, (int) Math.ceil(remaining)); }
    public String getMessage() { return message; }
    public boolean isPaused() { return paused; }
    public void setComputerEnabled(boolean enabled) { computer = enabled; }
    public void setTraining(boolean enabled) { training = enabled; }
    public boolean isTraining() { return training; }
    public void setCrouching(boolean value) { crouchRequested = value; posture(player, value); }
    public void setEnemyCrouching(boolean value) { enemyCrouchRequested = value; posture(enemy, value); }
    private void posture(Fighter fighter, boolean requested) { fighter.crouching = requested && fighter.height == 0 && fighter.velocity == 0 && fighter.stun == 0; }
    public void setEnemyDirection(int value) { enemyDirection = Integer.compare(value, 0); }
    public void enemyJump() { if (!training && !computer) jump(enemy); }
    public boolean enemyAttack(Move move) { return !training && !computer && !paused  && state == State.FIGHT && attack(enemy, move); }

    public void restart() {
        player.wins = enemy.wins = 0;
        round = 1; accumulator = 0; paused = false;
        beginRound();
    }
    private void reset(Fighter fighter, double x, int facing) {
        fighter.x = x; fighter.facing = facing; fighter.height = fighter.velocity = fighter.stun = fighter.flash = 0;
        fighter.health = 100; fighter.energy = 40; fighter.move = null; fighter.guard = fighter.crouching = false; fighter.connected = false;
    }
    private void beginRound() {
        reset(player, 210, 1); reset(enemy, 510, -1);
        direction = enemyDirection = 0; crouchRequested = enemyCrouchRequested = false; aiDecision = aiGuard = 0;
        remaining = training ? 0 : 60; winner = 0;
        if (training) {
            player.x = 360; player.energy = 100;
            stateTime = 0; state = State.FIGHT; message = "";
        } else {
            stateTime = 1.4; state = State.READY; message = "ROUND " + round; sound(Sound.ROUND);
        }
    }
    public void setDirection(int value) { direction = Integer.compare(value, 0); }
    public void releaseControls() { direction = enemyDirection = 0; crouchRequested = enemyCrouchRequested = false; player.guard = enemy.guard = player.crouching = enemy.crouching = false; }
    public void togglePause() { paused = !paused; releaseControls(); }
    public void jump() { jump(player); }
    private void jump(Fighter fighter) {
        if (!paused && state == State.FIGHT && fighter.height == 0 && !fighter.crouching && fighter.stun == 0 && fighter.move == null) {
            fighter.velocity = 560; fighter.guard = false; sound(Sound.JUMP);
        }
    }
    public boolean attack(Move move) { return !paused  && state == State.FIGHT && attack(player, move); }
    private boolean attack(Fighter fighter, Move move) {
        if (fighter.move != null || fighter.stun > 0 || fighter.health <= 0) return false;
        if (move == Move.SPECIAL && fighter.energy < 40) return false;
        if (move == Move.SPECIAL) fighter.energy -= 40;
        fighter.guard = false;
        fighter.move = move; fighter.attackTime = 0; fighter.attackFacing = fighter.facing; fighter.connected = false;
        sound(move == Move.SPECIAL ? Sound.SPECIAL : move.isKick() ? Sound.KICK : Sound.PUNCH);
        return true;
    }
    /** Split real time into fixed steps; a stall can advance at most 250 ms. */
    public void update(double seconds) {
        if (paused || !Double.isFinite(seconds) || seconds <= 0) return;
        accumulator += Math.min(seconds, .25);
        while (accumulator + 1e-9 >= 1.0 / 60) { tick(1.0 / 60); accumulator -= 1.0 / 60; }
    }
    private void tick(double dt) {
        if (state == State.MATCH_OVER) return;
        if (state != State.FIGHT) {
            stateTime -= dt;
            if (stateTime <= 0) {
                if (state == State.READY) { state = State.FIGHT; stateTime = .6; message = "FIGHT!"; sound(Sound.FIGHT); }
                else { round++; beginRound(); }
            }
            return;
        }
        stateTime = Math.max(0, stateTime - dt);
        if (stateTime == 0) message = "";
        posture(player, crouchRequested); posture(enemy, !computer && enemyCrouchRequested);
        if (training) {
            // Solo practice has no opponent: no AI, collision, hit detection, or result.
            player.energy = 100;
            if (direction != 0 && player.move == null) player.facing = direction;
            player.guard = false;
            move(player, player.guard ? 0 : direction, dt);
            advance(player, dt);
            return;
        }
        remaining = Math.max(0, remaining - dt);
        player.facing = player.x <= enemy.x ? 1 : -1;
        enemy.facing = -player.facing;
        player.guard = backGuard(player, direction);
        if (computer) think(dt);
        else enemy.guard = backGuard(enemy, enemyDirection);
        move(player, player.guard ? 0 : direction, dt);
        move(enemy, computer ? (enemy.move == null && !enemy.guard && Math.abs(enemy.x - player.x) > 108 ? enemy.facing : 0) : (enemy.guard ? 0 : enemyDirection), dt);
        separate();
        advance(player, dt); advance(enemy, dt);
        // Capture both hit decisions before applying damage, allowing simultaneous knockouts.
        int damageToEnemy = hit(player, enemy), damageToPlayer = hit(enemy, player);
        if (damageToEnemy > 0) damage(enemy, player, damageToEnemy);
        if (damageToPlayer > 0) damage(player, enemy, damageToPlayer);
        if (player.health <= 0 || enemy.health <= 0 || remaining <= 1e-8) endRound();
    }
    /** Facing is recalculated each step, so back changes immediately after a side switch. */
    private boolean backGuard(Fighter fighter, int inputDirection) {
        return inputDirection == -fighter.facing && fighter.move == null && fighter.stun == 0
                && fighter.height == 0 && fighter.velocity == 0 && fighter.health > 0;
    }
    private void think(double dt) {
        aiDecision -= dt; aiGuard = Math.max(0, aiGuard - dt);
        enemy.guard = aiGuard > 0 && enemy.move == null && enemy.stun == 0 && enemy.height == 0;
        if (aiDecision > 0 || enemy.stun > 0 || enemy.move != null) return;
        aiDecision = .20 + random.nextDouble() * .28;
        double distance = Math.abs(enemy.x - player.x);
        if (player.move != null && distance < 180 && random.nextDouble() < .32) {
            aiGuard = .42; enemy.guard = true;
        } else if (distance < 125) {
            double choice = random.nextDouble();
            attack(enemy, choice < .16 && enemy.energy >= 40 ? Move.SPECIAL : choice < .56 ? Move.KICK : Move.PUNCH);
        }
    }
    private void move(Fighter fighter, int direction, double dt) {
        int character = fighter == player ? playerCharacter : enemyCharacter;
        if (fighter.stun == 0 && fighter.move == null && !fighter.crouching) fighter.x += direction * (155 + character % 4 * 12) * dt;
        fighter.x = Math.max(65, Math.min(655, fighter.x));
        if (fighter.height > 0 || fighter.velocity > 0) {
            fighter.height = Math.max(0, fighter.height + fighter.velocity * dt);
            fighter.velocity -= 1250 * dt;
            if (fighter.height == 0 && fighter.velocity < 0) { fighter.velocity = 0; sound(Sound.LAND); }
        }
    }
    private void separate() {
        double gap = enemy.x - player.x;
        if (Math.abs(gap) < 54 && Math.abs(player.height - enemy.height) < 90) {
            double center = Math.max(92, Math.min(628, (player.x + enemy.x) / 2));
            int sign = gap >= 0 ? 1 : -1;
            player.x = center - sign * 27; enemy.x = center + sign * 27;
        }
    }
    private void advance(Fighter fighter, double dt) {
        fighter.stun = Math.max(0, fighter.stun - dt); fighter.flash = Math.max(0, fighter.flash - dt);
        if (fighter.move != null) {
            fighter.attackTime += dt;
            if (fighter.attackTime >= fighter.move.windup + fighter.move.active + fighter.move.recovery) fighter.move = null;
        }
    }
    private int hit(Fighter attacker, Fighter target) {
        Move move = attacker.move;
        if (move == null || attacker.connected || attacker.attackTime < move.windup
                || attacker.attackTime > move.windup + move.active) return 0;
        double distance = (target.x - attacker.x) * attacker.attackFacing;
        if (distance < 0 || distance > move.reach || Math.abs(attacker.height - target.height) > 85) return 0;
        attacker.connected = true;
        int character = attacker == player ? playerCharacter : enemyCharacter;
        int damage = move.damage + character % 3;
        return target.guard && target.facing == -attacker.attackFacing ? Math.max(1, damage / 5) : damage;
    }
    private void damage(Fighter target, Fighter attacker, int amount) {
        boolean guarded = target.guard && target.facing == -attacker.attackFacing;
        sound(guarded ? Sound.BLOCK : Sound.HIT);
        target.health = Math.max(0, target.health - amount);
        target.energy = Math.min(100, target.energy + (guarded ? 4 : 8));
        attacker.energy = Math.min(100, attacker.energy + (guarded ? 3 : 9));
        target.flash = .15;
        target.stun = guarded ? .08 : .21;
        if (!guarded) { target.move = null; target.x = Math.max(65, Math.min(655, target.x + attacker.attackFacing * 14)); }
    }
    private void endRound() {
        winner = player.health == enemy.health ? 0 : player.health > enemy.health ? 1 : 2;
        if (winner == 1) player.wins++;
        if (winner == 2) enemy.wins++;
        state = State.MATCH_OVER;
        message = winner == 0 ? "DRAW" : winner == 1 ? "1P WINS" : computer ? "CPU WINS" : "2P WINS";
        sound(player.health <= 0 || enemy.health <= 0 ? Sound.KO : Sound.TIME_UP);
        if (state == State.MATCH_OVER) sound(winner == 0 ? Sound.DRAW : winner == 1 ? Sound.WIN : Sound.LOSE);
        else if (winner == 0) sound(Sound.DRAW);
        stateTime = 2.4;
        releaseControls();
    }
}
