package com.android.view;

/** Android-compatible gravity flags. Layout direction is 0 (LTR) or 1 (RTL). */
public final class Gravity {
    private Gravity() {}
    public static final int NO_GRAVITY=0, AXIS_SPECIFIED=1, AXIS_PULL_BEFORE=2,
            AXIS_PULL_AFTER=4, AXIS_CLIP=8, AXIS_X_SHIFT=0, AXIS_Y_SHIFT=4;
    public static final int TOP=48, BOTTOM=80, LEFT=3, RIGHT=5,
            CENTER_VERTICAL=16, FILL_VERTICAL=112, CENTER_HORIZONTAL=1,
            FILL_HORIZONTAL=7, CENTER=17, FILL=119,
            CLIP_VERTICAL=128, CLIP_HORIZONTAL=8,
            HORIZONTAL_GRAVITY_MASK=7, VERTICAL_GRAVITY_MASK=112,
            RELATIVE_LAYOUT_DIRECTION=0x00800000,
            START=RELATIVE_LAYOUT_DIRECTION|LEFT, END=RELATIVE_LAYOUT_DIRECTION|RIGHT,
            RELATIVE_HORIZONTAL_GRAVITY_MASK=RELATIVE_LAYOUT_DIRECTION|HORIZONTAL_GRAVITY_MASK;
    public static int getAbsoluteGravity(int gravity,int layoutDirection) {
        if(layoutDirection!=0&&layoutDirection!=1)throw new IllegalArgumentException("layoutDirection");
        if((gravity&RELATIVE_LAYOUT_DIRECTION)!=0) {
            int horizontal=gravity&HORIZONTAL_GRAVITY_MASK;
            if(horizontal==LEFT||horizontal==RIGHT) {
                if(layoutDirection==1)horizontal=horizontal==LEFT?RIGHT:LEFT;
                gravity=(gravity&~HORIZONTAL_GRAVITY_MASK)|horizontal;
            }
            gravity&=~RELATIVE_LAYOUT_DIRECTION;
        }
        return gravity;
    }
}
