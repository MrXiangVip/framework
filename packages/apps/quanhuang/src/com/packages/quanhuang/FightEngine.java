package com.packages.quanhuang;

import java.util.Random;

/** Deterministic 60 Hz combat simulation, independent of the window and rendering. */
public final class FightEngine {
    public enum Sound { ROUND, FIGHT, PUNCH, KICK, SPECIAL, HIT, BLOCK, JUMP, LAND, KO, TIME_UP, WIN, LOSE, DRAW, SELECT }
    private java.util.function.Consumer<Sound> soundListener = sound -> {};
    public void setSoundListener(java.util.function.Consumer<Sound> listener) { soundListener = java.util.Objects.requireNonNull(listener); }
    private void sound(Sound sound) { soundListener.accept(sound); }
    public enum State { READY, FIGHT, ROUND_OVER, MATCH_OVER }
    // All action states belong to the simulation, including locked movement.
    public enum FighterState {
        IDLE, WALK_LEFT, WALK_RIGHT, CROUCH, DASH_FORWARD, DASH_BACK, ROLL_RIGHT, ROLL_LEFT,
        JUMP_UP, JUMP_LEFT, JUMP_RIGHT, GUARD_STAND, GUARD_CROUCH, ATTACK, HITSTUN, KNOCKDOWN
    }
    public enum Move {
        LIGHT_PUNCH(8, 100, .08, .11, .22), LIGHT_KICK(12, 130, .16, .12, .29),
        HEAVY_PUNCH(17, 115, .18, .12, .32), HEAVY_KICK(20, 145, .22, .12, .36),
        CROUCH_LP(7, 95, .07, .10, .20), CROUCH_LK(10, 125, .12, .12, .25),
        JUMP_LP(8, 100, .07, .12, .20), JUMP_LK(12, 130, .10, .14, .24),
        SPECIAL(26, 185, .22, .16, .40);
        // Keep existing engine/audio test clients source-compatible.
        public static final Move PUNCH = LIGHT_PUNCH, KICK = LIGHT_KICK;
        public boolean isKick() {
            return this == LIGHT_KICK || this == HEAVY_KICK || this == CROUCH_LK || this == JUMP_LK;
        }
        final int damage, reach;
        final double windup, active, recovery;
        Move(int damage, int reach, double windup, double active, double recovery) {
            this.damage = damage; this.reach = reach; this.windup = windup; this.active = active; this.recovery = recovery;
        }
    }
    public static final class Fighter {
        double x, height, velocity, stun, attackTime, flash;
        double impactTime, impactX, impactY;
        int impactFacing, impactPower;
        boolean impactGuarded;
        int health = 100, energy = 40, facing, attackFacing, wins;
        boolean guard, connected;
        Move move;
        FighterState state = FighterState.IDLE, jumpState = FighterState.JUMP_UP;
        double actionTime, actionDuration, horizontal, invulnerable;
        public FighterState getState() { return state; }
        Fighter(double x, int facing) { this.x = x; this.facing = facing; }
        public double getX() { return x; }
        public double getHeight() { return height; }
        public int getHealth() { return health; }
        public int getEnergy() { return energy; }
        public int getWins() { return wins; }
        public int getFacing() { return move == null ? facing : attackFacing; }
        public boolean isGuarding() { return guard; }
        public boolean isHit() { return flash > 0; }
        public Move getMove() { return move; }
        public double getAttackTime() { return attackTime; }
    }

    final Fighter player = new Fighter(210, 1), enemy = new Fighter(510, -1);
    private final Random random;
    private State state;
    private double accumulator, stateTime, remaining, aiDecision, aiGuard;
    private int direction, round, winner;
    private boolean blocking, paused, computer = true;
    private String message;
    private static final int A = 1, D = 2, W = 4, S = 8, L = 16, J = 32, K = 64, I = 128;
    private int keys, pressed, pressedHeld, doubleTaps;
    private double inputTime, lastATime = Double.NEGATIVE_INFINITY, lastDTime = Double.NEGATIVE_INFINITY;
    private final InputBuffer inputBuffer = new InputBuffer();

    // Ring history expires after six simulation frames, even while actions are locked.
    private static final class InputBuffer {
        final int[] held = new int[6], edges = new int[6], taps = new int[6];
        int head;
        void push(int keys, int pressed, int doubles) {
            head = (head + 1) % held.length;
            held[head] = keys; edges[head] = pressed; taps[head] = doubles;
        }
        void clear() { java.util.Arrays.fill(edges, 0); java.util.Arrays.fill(taps, 0); }
    }

    /** Only physical key states cross the view/engine boundary. */
    public void setInput(boolean a, boolean d, boolean w, boolean s, boolean l, boolean j, boolean k, boolean i) {
        int next = (a ? A : 0) | (d ? D : 0) | (w ? W : 0) | (s ? S : 0)
                | (l ? L : 0) | (j ? J : 0) | (k ? K : 0) | (i ? I : 0);
        if (paused || state != State.FIGHT) return;
        int down = next & ~keys;
        // Real release/press transitions count; keyboard repeat cannot create a dash.
        if ((down & A) != 0) {
            if (inputTime - lastATime <= .2 + 1e-9) { doubleTaps |= A; lastATime = Double.NEGATIVE_INFINITY; }
            else lastATime = inputTime;
        }
        if ((down & D) != 0) {
            if (inputTime - lastDTime <= .2 + 1e-9) { doubleTaps |= D; lastDTime = Double.NEGATIVE_INFINITY; }
            else lastDTime = inputTime;
        }
        // Preserve a quick press/release that occurs between two simulation ticks.
        if (down != 0) pressedHeld = next;
        keys = next; pressed |= down;
    }

    public FightEngine() { this(2026); }
    public FightEngine(long seed) { random = new Random(seed); restart(); }
    public Fighter getPlayer() { return player; }
    public Fighter getEnemy() { return enemy; }
    public State getState() { return state; }
    public int getRound() { return round; }
    public int getWinner() { return winner; }
    public int getSeconds() { return Math.max(0, (int) Math.ceil(remaining)); }
    public String getMessage() { return message; }
    public boolean isPaused() { return paused; }
    public void setComputerEnabled(boolean enabled) { computer = enabled; }

    public void restart() {
        player.wins = enemy.wins = 0;
        round = 1; accumulator = 0; paused = false;
        beginRound();
    }
    private void reset(Fighter fighter, double x, int facing) {
        fighter.x = x; fighter.facing = facing; fighter.height = fighter.velocity = fighter.stun = fighter.flash = 0;
        fighter.impactTime = 0;
        fighter.state = FighterState.IDLE; fighter.jumpState = FighterState.JUMP_UP;
        fighter.actionTime = fighter.actionDuration = fighter.horizontal = fighter.invulnerable = 0;
        fighter.health = 100; fighter.energy = 40; fighter.move = null; fighter.guard = false; fighter.connected = false;
    }
    private void beginRound() {
        reset(player, 210, 1); reset(enemy, 510, -1);
        releaseControls(); aiDecision = aiGuard = 0;
        remaining = 60; stateTime = 1.4; state = State.READY; winner = 0; message = "ROUND " + round; sound(Sound.ROUND);
    }
    public void setDirection(int value) { direction = Integer.compare(value, 0); }
    public void setBlocking(boolean value) { blocking = value; if (!value) player.guard = false; }
    public void releaseControls() {
        direction = 0; blocking = false; player.guard = false;
        keys = pressed = pressedHeld = doubleTaps = 0; inputBuffer.clear();
        lastATime = lastDTime = Double.NEGATIVE_INFINITY;
    }
    public void togglePause() { paused = !paused; releaseControls(); }
    // Compatibility entry points remain in the engine, never called by FightView.
    public void jump() {
        if (!paused && state == State.FIGHT && actionable(player) && !airborne(player)) startJump(player, 0);
    }
    public boolean attack(Move move) {
        return !paused && !blocking && state == State.FIGHT && attack(player, move);
    }
    private static boolean airborne(Fighter f) { return f.height > 0 || f.velocity > 0; }
    private static boolean actionable(Fighter f) {
        return f.move == null && f.stun == 0 && f.actionDuration == 0 && f.health > 0;
    }
    private boolean attack(Fighter fighter, Move move) {
        if (move == null || !actionable(fighter) || fighter.guard) return false;
        // Airborne fighters can only use the two jumping attacks.
        if (airborne(fighter) && move != Move.JUMP_LP && move != Move.JUMP_LK) return false;
        if (move == Move.SPECIAL && fighter.energy < 40) return false;
        if (move == Move.SPECIAL) fighter.energy -= 40;
        fighter.move = move; fighter.attackTime = 0; fighter.attackFacing = fighter.facing; fighter.connected = false;
        fighter.state = FighterState.ATTACK;
        sound(move == Move.SPECIAL ? Sound.SPECIAL : move.isKick() ? Sound.KICK : Sound.PUNCH);
        return true;
    }
    private void startJump(Fighter f, int direction) {
        f.velocity = 560; f.horizontal = direction * 170; f.guard = false;
        f.jumpState = direction < 0 ? FighterState.JUMP_LEFT : direction > 0 ? FighterState.JUMP_RIGHT : FighterState.JUMP_UP;
        f.state = f.jumpState; sound(Sound.JUMP);
    }
    private void startMotion(Fighter f, FighterState state, int direction) {
        f.state = state; f.guard = false; f.actionTime = 0;
        // Dash: 3 startup + 9 travel + 6 recovery frames. Roll: 24 locked frames.
        boolean roll = state == FighterState.ROLL_LEFT || state == FighterState.ROLL_RIGHT;
        f.actionDuration = roll ? 24.0 / 60 : 18.0 / 60;
        f.horizontal = direction * (roll ? 290 : 500);
    }
    private void readPlayerInput() {
        int held = pressed != 0 ? pressedHeld : keys;
        // Air input never queues a ground action to execute automatically upon landing.
        int edges = pressed, taps = doubleTaps;
        if (airborne(player)) {
            edges = (held & W) != 0 && (held & (J | K)) != 0 ? edges & (W | J | K) : 0;
            taps = 0;
        }
        inputBuffer.push(held, edges, taps); pressed = doubleTaps = 0;
        if (!actionable(player)) return;
        player.guard = false;
        // Current defensive intent wins over buffered attacks; W excludes crouching.
        boolean crouch = (keys & S) != 0 && (keys & W) == 0;
        if (!airborne(player) && (keys & L) != 0
                && (crouch || (keys & (J | K)) == 0)) {
            player.guard = true;
            player.state = crouch ? FighterState.GUARD_CROUCH : FighterState.GUARD_STAND;
            inputBuffer.clear(); return;
        }
        // Match newest buffered input first, then consume it exactly once.
        for (int age = 0; age < 6; age++) {
            int slot = (inputBuffer.head - age + 6) % 6;
            if (resolveInput(inputBuffer.held[slot], inputBuffer.edges[slot], inputBuffer.taps[slot])) {
                inputBuffer.clear(); return;
            }
        }
        if (airborne(player)) { player.state = player.jumpState; return; }
        if (crouch) {
            int dir = (keys & D) != 0 ? 1 : (keys & A) != 0 ? -1 : 0;
            if (dir != 0 && (keys & L) == 0) {
                startMotion(player, dir > 0 ? FighterState.ROLL_RIGHT : FighterState.ROLL_LEFT, dir); return;
            }
            player.state = FighterState.CROUCH; return;
        }
        int dir = ((keys & D) != 0 ? 1 : 0) - ((keys & A) != 0 ? 1 : 0);
        player.state = dir < 0 ? FighterState.WALK_LEFT : dir > 0 ? FighterState.WALK_RIGHT : FighterState.IDLE;
    }
    private boolean resolveInput(int held, int edge, int taps) {
        boolean w = (held & W) != 0, s = (held & S) != 0 && !w, l = (held & L) != 0;
        boolean j = (held & J) != 0, k = (held & K) != 0;
        boolean attackEdge = (edge & (J | K | L | S | W)) != 0;
        if (airborne(player)) {
            // Ground commands cannot interrupt flight; require W for air attacks.
            return w && attackEdge && (j || k) && attack(player, j ? Move.JUMP_LP : Move.JUMP_LK);
        }
        if (s && l) return false;
        if (attackEdge && l && j) return attack(player, Move.HEAVY_PUNCH);
        if (attackEdge && l && k) return attack(player, Move.HEAVY_KICK);
        if (attackEdge && s && j) return attack(player, Move.CROUCH_LP);
        if (attackEdge && s && k) return attack(player, Move.CROUCH_LK);
        if (attackEdge && w && (j || k)) {
            startJump(player, ((held & D) != 0 ? 1 : 0) - ((held & A) != 0 ? 1 : 0));
            return attack(player, j ? Move.JUMP_LP : Move.JUMP_LK);
        }
        if ((taps & D) != 0) { startMotion(player, FighterState.DASH_FORWARD, player.facing); return true; }
        if ((taps & A) != 0) { startMotion(player, FighterState.DASH_BACK, -player.facing); return true; }
        if (s && !l && (held & (D | A)) != 0 && (edge & (S | D | A)) != 0) {
            boolean right = (held & D) != 0;
            startMotion(player, right ? FighterState.ROLL_RIGHT : FighterState.ROLL_LEFT, right ? 1 : -1); return true;
        }
        if (w && (edge & W) != 0) {
            startJump(player, ((held & D) != 0 ? 1 : 0) - ((held & A) != 0 ? 1 : 0)); return true;
        }
        if (!l && !s && !w && (edge & J) != 0) return attack(player, Move.LIGHT_PUNCH);
        if (!l && !s && !w && (edge & K) != 0) return attack(player, Move.LIGHT_KICK);
        return (edge & I) != 0 && attack(player, Move.SPECIAL);
    }
    /** Split real time into fixed steps; a stall can advance at most 250 ms. */
    public void update(double seconds) {
        if (paused || !Double.isFinite(seconds) || seconds <= 0) return;
        accumulator += Math.min(seconds, .25);
        while (accumulator + 1e-9 >= 1.0 / 60) { tick(1.0 / 60); accumulator -= 1.0 / 60; }
    }
    private void tick(double dt) {
        // Presentation lifetimes follow simulation time, including KO, and freeze on pause.
        player.impactTime = Math.max(0, player.impactTime - dt);
        enemy.impactTime = Math.max(0, enemy.impactTime - dt);
        if (state == State.MATCH_OVER) return;
        if (state != State.FIGHT) {
            stateTime -= dt;
            if (stateTime <= 0) {
                if (state == State.READY) { state = State.FIGHT; stateTime = .6; message = "FIGHT!"; sound(Sound.FIGHT); }
                else { round++; beginRound(); }
            }
            return;
        }
        inputTime += dt;
        stateTime = Math.max(0, stateTime - dt);
        if (stateTime == 0) message = "";
        remaining = Math.max(0, remaining - dt);
        player.facing = player.x <= enemy.x ? 1 : -1;
        enemy.facing = -player.facing;
        readPlayerInput();
        // Legacy direct controls are retained only for engine clients.
        if (blocking && actionable(player) && !airborne(player)) {
            player.guard = true; player.state = FighterState.GUARD_STAND;
        }
        if (computer) think(dt);
        else enemy.guard = false;
        int walking = player.state == FighterState.WALK_LEFT ? -1 : player.state == FighterState.WALK_RIGHT ? 1 : direction;
        move(player, player.guard ? 0 : walking, dt);
        int enemyWalking = computer && actionable(enemy) && !enemy.guard && Math.abs(enemy.x - player.x) > 108 ? enemy.facing : 0;
        // CPU actions expose the same states as player actions to the renderer.
        if (actionable(enemy)) enemy.state = enemy.guard ? FighterState.GUARD_STAND
                : enemyWalking < 0 ? FighterState.WALK_LEFT : enemyWalking > 0 ? FighterState.WALK_RIGHT : FighterState.IDLE;
        move(enemy, enemyWalking, dt);
        separate();
        // Crossing the opponent updates facing; locked motion keeps its launch direction.
        player.facing = player.x <= enemy.x ? 1 : -1; enemy.facing = -player.facing;
        advance(player, dt); advance(enemy, dt);
        // Capture both hit decisions before applying damage, allowing simultaneous knockouts.
        int damageToEnemy = hit(player, enemy), damageToPlayer = hit(enemy, player);
        if (damageToEnemy > 0) damage(enemy, player, damageToEnemy);
        if (damageToPlayer > 0) damage(player, enemy, damageToPlayer);
        if (player.health <= 0 || enemy.health <= 0 || remaining <= 1e-8) endRound();
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
        // Movement locks and invulnerability windows use simulation seconds.
        if (fighter.actionDuration > 0) {
            double phase = fighter.actionTime + 1e-9;
            boolean roll = fighter.state == FighterState.ROLL_LEFT || fighter.state == FighterState.ROLL_RIGHT;
            fighter.invulnerable = roll && phase >= 3.0 / 60 && phase < 11.0 / 60 ? dt : 0;
            if (roll || (phase >= 3.0 / 60 && phase < 12.0 / 60))
                fighter.x += fighter.horizontal * dt;
            fighter.actionTime += dt;
            if (fighter.actionTime + 1e-9 >= fighter.actionDuration) {
                fighter.actionDuration = fighter.invulnerable = 0; fighter.state = FighterState.IDLE;
            }
        } else if (airborne(fighter)) fighter.x += fighter.horizontal * dt;
        else if (fighter.stun == 0 && fighter.move == null) fighter.x += direction * 170 * dt;
        fighter.x = Math.max(65, Math.min(655, fighter.x));
        if (fighter.height > 0 || fighter.velocity > 0) {
            fighter.height = Math.max(0, fighter.height + fighter.velocity * dt);
            fighter.velocity -= 1250 * dt;
            if (fighter.height == 0 && fighter.velocity < 0) {
                fighter.velocity = fighter.horizontal = 0;
                if (fighter.move == null && fighter.stun == 0) fighter.state = FighterState.IDLE;
                sound(Sound.LAND);
            }
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
            if (fighter.attackTime + 1e-9 >= fighter.move.windup + fighter.move.active + fighter.move.recovery) fighter.move = null;
        }
        // End recovery in the appropriate grounded/airborne state.
        if (fighter.stun == 0 && fighter.move == null && fighter.actionDuration == 0
                && (fighter.state == FighterState.ATTACK || fighter.state == FighterState.HITSTUN))
            fighter.state = airborne(fighter) ? fighter.jumpState : FighterState.IDLE;
    }
    private int hit(Fighter attacker, Fighter target) {
        if (target.invulnerable > 0) return 0;
        Move move = attacker.move;
        if (move == null || attacker.connected || attacker.attackTime < move.windup
                || attacker.attackTime > move.windup + move.active) return 0;
        double distance = (target.x - attacker.x) * attacker.attackFacing;
        if (distance < 0 || distance > move.reach || Math.abs(attacker.height - target.height) > 85) return 0;
        attacker.connected = true;
        return target.guard && target.facing == -attacker.attackFacing ? Math.max(1, move.damage / 5) : move.damage;
    }
    private void damage(Fighter target, Fighter attacker, int amount) {
        boolean guarded = target.guard && target.facing == -attacker.attackFacing;
        sound(guarded ? Sound.BLOCK : Sound.HIT);
        target.health = Math.max(0, target.health - amount);
        target.energy = Math.min(100, target.energy + (guarded ? 4 : 8));
        attacker.energy = Math.min(100, attacker.energy + (guarded ? 3 : 9));
        target.impactTime = .24;
        target.impactX = target.x - attacker.attackFacing * 22;
        target.impactY = 540 - target.height - 106;
        target.impactFacing = attacker.attackFacing;
        target.impactPower = amount;
        target.impactGuarded = guarded;
        target.flash = .15;
        target.stun = guarded ? .08 : .21;
        // A hit interrupts dash/roll and clears their invulnerability.
        target.actionDuration = target.invulnerable = 0;
        target.state = target.health == 0 ? FighterState.KNOCKDOWN : FighterState.HITSTUN;
        if (!guarded) { target.move = null; target.x = Math.max(65, Math.min(655, target.x + attacker.attackFacing * 14)); }
    }
    private void endRound() {
        winner = player.health == enemy.health ? 0 : player.health > enemy.health ? 1 : 2;
        if (winner == 1) player.wins++;
        if (winner == 2) enemy.wins++;
        state = player.wins >= 2 || enemy.wins >= 2 ? State.MATCH_OVER : State.ROUND_OVER;
        message = state == State.MATCH_OVER ? (winner == 1 ? "YOU WIN" : "TRY AGAIN")
                : winner == 0 ? "DRAW" : remaining <= 1e-8 ? "TIME UP" : "K.O.";
        sound(player.health <= 0 || enemy.health <= 0 ? Sound.KO : Sound.TIME_UP);
        if (state == State.MATCH_OVER) sound(winner == 1 ? Sound.WIN : Sound.LOSE);
        else if (winner == 0) sound(Sound.DRAW);
        stateTime = 2.4;
        releaseControls();
    }
}
