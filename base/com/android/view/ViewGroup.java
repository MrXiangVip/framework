package com.android.view;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.util.Log;

public abstract  class ViewGroup extends View implements ViewParent {

    private View[] mChildren;
    private View mTouchTarget;

    private int mChildrenCount;
    private static final int ARRAY_INITIAL_CAPACITY = 12;
    private static final int ARRAY_CAPACITY_INCREMENT = 12;
    private String TAG="ViewGroup.";

    public ViewGroup(Context context) {
        this(context, null);
    }

    public ViewGroup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        initViewGroup();
//        initFromAttributes(context, attrs, defStyleAttr, defStyleRes);
    }
    private void initViewGroup() {
        mChildren = new View[ARRAY_INITIAL_CAPACITY];
        mChildrenCount = 0;
    }
    public void addView(View child, LayoutParams params) {
        Log.d(TAG, "addView "+child);
        addView(child, -1, params);
    }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new NullPointerException("child");
        if (child.mParent != null) throw new IllegalStateException("View already has a parent");
        addViewInner(child, index, params, false);
        requestLayout();

    }

    private void addViewInner(View child, int index, LayoutParams params,
                              boolean preventRequestLayout) {
        if (preventRequestLayout) {
            child.mLayoutParams = params;
        } else {
            child.setLayoutParams(params);
        }
        if (index < 0) {
            index = mChildrenCount;
        }
        addInArray(child, index);
        child.mParent = this;

    }
    private void addInArray(View child, int index) {
        View[] children = mChildren;
        final int count = mChildrenCount;
        final int size = children.length;
        if (index == count) {
            if (size == count) {
                mChildren = new View[size + ARRAY_CAPACITY_INCREMENT];
                System.arraycopy(children, 0, mChildren, 0, size);
                children = mChildren;
            }
            children[mChildrenCount++] = child;
        } else if (index < count) {
            if (size == count) {
                mChildren = new View[size + ARRAY_CAPACITY_INCREMENT];
                System.arraycopy(children, 0, mChildren, 0, index);
                System.arraycopy(children, index, mChildren, index + 1, count - index);
                children = mChildren;
            } else {
                System.arraycopy(children, index, children, index + 1, count - index);
            }
            children[index] = child;
            mChildrenCount++;
//            if (mLastTouchDownIndex >= index) {
//                mLastTouchDownIndex++;
//            }
        } else {
            throw new IndexOutOfBoundsException("index=" + index + " count=" + count);
        }
    }

    @Override public View findViewById(String id) {
        View found = super.findViewById(id);
        for (int i = 0; found == null && i < mChildrenCount; i++) found = mChildren[i].findViewById(id);
        return found;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        for (int i = 0; i < mChildrenCount; i++) {
            View child = mChildren[i];
            if (child.getWidth() > 0 && child.getHeight() > 0 && child.dispatchKeyEvent(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Hover and wheel target the pointer location without taking a click/drag capture.
        if (event.getAction() == MotionEvent.ACTION_SCROLL || event.getAction() == MotionEvent.ACTION_HOVER) {
            for (int i = mChildrenCount - 1; i >= 0; i--) {
                View child = mChildren[i];
                int x = event.getX() - child.getLeft(), y = event.getY() - child.getTop();
                if (x >= 0 && y >= 0 && x < child.getWidth() && y < child.getHeight()
                        && child.dispatchTouchEvent(event.offset(-child.getLeft(), -child.getTop()))) return true;
            }
            return super.dispatchTouchEvent(event);
        }
        // Focus loss/closure also cancels keyboard-driven child state without an active pointer target.
        if (event.getAction() == MotionEvent.ACTION_CANCEL && mTouchTarget == null) {
            for (int i = 0; i < mChildrenCount; i++) {
                View child = mChildren[i];
                child.dispatchTouchEvent(event.offset(-child.getLeft(), -child.getTop()));
            }
            return super.dispatchTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mTouchTarget != null) mTouchTarget.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
            mTouchTarget = null;
            for (int i = mChildrenCount - 1; i >= 0; i--) {
                View child = mChildren[i];
                int x = event.getX() - child.getLeft(), y = event.getY() - child.getTop();
                if (x >= 0 && y >= 0 && x < child.getWidth() && y < child.getHeight()
                        && child.dispatchTouchEvent(event.offset(-child.getLeft(), -child.getTop()))) {
                    mTouchTarget = child;
                    return true;
                }
            }
        } else if (mTouchTarget != null) {
            View target = mTouchTarget;
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
                mTouchTarget = null;
            return target.dispatchTouchEvent(event.offset(-target.getLeft(), -target.getTop()));
        }
        return super.dispatchTouchEvent(event);
    }

    public void removeAllViews() {
        if (mTouchTarget != null) mTouchTarget.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
        mTouchTarget = null;
        for (int i = 0; i < mChildrenCount; i++) {
            mChildren[i].mParent = null;
            mChildren[i] = null;
        }
        mChildrenCount = 0;
        requestLayout();
    }

    public int getChildCount() {
        return mChildrenCount;
    }

    public View getChildAt(int index) {
        if (index < 0 || index >= mChildrenCount) {
            return null;
        }
        return mChildren[index];
    }
    protected void measureChildWithMargins(View child,
                                           int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        LayoutParams params = child.getLayoutParams();
        child.measure(getChildMeasureSpec(parentWidthMeasureSpec, 2 * mPadding + widthUsed, params.width),
                getChildMeasureSpec(parentHeightMeasureSpec, 2 * mPadding + heightUsed, params.height));

    }
    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int mode = MeasureSpec.getMode(spec);
        int available = Math.max(0, MeasureSpec.getSize(spec) - padding);
        if (childDimension >= 0) return MeasureSpec.makeMeasureSpec(childDimension, MeasureSpec.EXACTLY);
        if (mode == MeasureSpec.UNSPECIFIED) return MeasureSpec.makeMeasureSpec(0, mode);
        int childMode = childDimension == LayoutParams.MATCH_PARENT && mode == MeasureSpec.EXACTLY
                ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
        return MeasureSpec.makeMeasureSpec(available, childMode);
    }

    public final void layout(int l, int t, int r, int b) {

        super.layout(l, t, r, b);
    }
    protected void dispatchDraw(Canvas canvas) {
        final int childrenCount = mChildrenCount;
        final View[] children = mChildren;
        final long drawingTime = 0;

        for (int i = 0; i < childrenCount; i++) {

            View child = children[i];
            drawChild(canvas, child, drawingTime);
        }
    }
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return child.draw(canvas, this, drawingTime);
    }

    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams{
        public static final int FILL_PARENT = -1;
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width;
        public int height;
        public LayoutParams(Context c, AttributeSet attrs) {
            width = attrs == null ? WRAP_CONTENT : attrs.dimension("layout_width", WRAP_CONTENT);
            height = attrs == null ? WRAP_CONTENT : attrs.dimension("layout_height", WRAP_CONTENT);
        }

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        LayoutParams() {
        }
        public LayoutParams(LayoutParams source) {
            this.width = source.width;
            this.height = source.height;
        }
    }
}
