package fixture.multi;
public final class Broken extends Shared {
    @Override protected void onCreate() {
        super.onCreate();
        throw new IllegalStateException("Deliberate Activity creation failure");
    }
}
