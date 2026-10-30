package com.android.widget;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.view.View;

/** Non-drawing zero-size anchor, positioned by ConstraintLayout.LayoutParams. */
public class Guideline extends View {
    public Guideline(Context c) { this(c,null); }
    public Guideline(Context c,AttributeSet a) { this(c,a,0); }
    public Guideline(Context c,AttributeSet a,int style) { this(c,a,style,0); }
    public Guideline(Context c,AttributeSet a,int style,int res) { super(c,a,style,res); }
    @Override protected void onMeasure(int w,int h) { setMeasuredDimension(0,0); }
    private ConstraintLayout.LayoutParams params() {
        if(!(getLayoutParams() instanceof ConstraintLayout.LayoutParams))throw new IllegalStateException("Guideline must have ConstraintLayout.LayoutParams");
        return (ConstraintLayout.LayoutParams)getLayoutParams();
    }
    public void setGuidelineBegin(int value) { if(value<0)throw new IllegalArgumentException("guideBegin");ConstraintLayout.LayoutParams p=params();p.guideBegin=value;p.guideEnd=-1;p.guidePercent=-1;requestLayout(); }
    public void setGuidelineEnd(int value) { if(value<0)throw new IllegalArgumentException("guideEnd");ConstraintLayout.LayoutParams p=params();p.guideBegin=-1;p.guideEnd=value;p.guidePercent=-1;requestLayout(); }
    public void setGuidelinePercent(float value) { if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("guidePercent");ConstraintLayout.LayoutParams p=params();p.guideBegin=-1;p.guideEnd=-1;p.guidePercent=value;requestLayout(); }
}
