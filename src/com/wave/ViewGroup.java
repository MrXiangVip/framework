package com.wave;

public abstract class ViewGroup extends View {

    private View[] mChildren;

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
//        requestLayout();
        addViewInner(child, index, params, false);

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

        }
    }
}
