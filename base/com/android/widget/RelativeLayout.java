package com.android.widget;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.view.Gravity;
import com.android.view.View;
import com.android.view.ViewGroup;
import java.util.*;

/** Relative positioning with string IDs; see docs/relativelayout.md for compatibility. */
public class RelativeLayout extends ViewGroup {
    public static final int TRUE=-1;
    public static final int LEFT_OF=0, RIGHT_OF=1, ABOVE=2, BELOW=3, ALIGN_BASELINE=4,
            ALIGN_LEFT=5, ALIGN_TOP=6, ALIGN_RIGHT=7, ALIGN_BOTTOM=8,
            ALIGN_PARENT_LEFT=9, ALIGN_PARENT_TOP=10, ALIGN_PARENT_RIGHT=11,
            ALIGN_PARENT_BOTTOM=12, CENTER_IN_PARENT=13, CENTER_HORIZONTAL=14,
            CENTER_VERTICAL=15, START_OF=16, END_OF=17, ALIGN_START=18,
            ALIGN_END=19, ALIGN_PARENT_START=20, ALIGN_PARENT_END=21;
    public static final int LAYOUT_DIRECTION_LTR=0, LAYOUT_DIRECTION_RTL=1;
    private static final int UNSET=Integer.MIN_VALUE;
    private static final String ENABLED="-1";
    private int direction, gravity=Gravity.START|Gravity.TOP, minWidth, minHeight;
    private String ignoreGravity;
    private Node[] nodes=new Node[0], horizontal=new Node[0], vertical=new Node[0];
    private final Map<String,Node> ids=new HashMap<>();
    private View baselineView;

    public RelativeLayout(Context c) { this(c,null); }
    public RelativeLayout(Context c,AttributeSet a) { this(c,a,0); }
    public RelativeLayout(Context c,AttributeSet a,int style) { this(c,a,style,0); }
    public RelativeLayout(Context c,AttributeSet a,int style,int res) {
        super(c,a,style,res);
        if(a!=null) {
            if(a.get("gravity")!=null)setGravity(parseGravity(a.get("gravity")));
            ignoreGravity=id(a.get("ignoreGravity"));
            minWidth=a.dimension("minWidth",0);minHeight=a.dimension("minHeight",0);
            String d=a.get("layoutDirection");
            if(d!=null) {
                if(!"ltr".equals(d)&&!"rtl".equals(d))throw new IllegalArgumentException("layoutDirection: "+d);
                direction="rtl".equals(d)?1:0;
            }
        }
    }
    public int getGravity() { return gravity; }
    public void setGravity(int value) {
        if((value&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)==0)value|=Gravity.START;
        if((value&Gravity.VERTICAL_GRAVITY_MASK)==0)value|=Gravity.TOP;
        gravity=value;requestLayout();
    }
    public void setHorizontalGravity(int value) { setGravity((gravity&~Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)|(value&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)); }
    public void setVerticalGravity(int value) { setGravity((gravity&~Gravity.VERTICAL_GRAVITY_MASK)|(value&Gravity.VERTICAL_GRAVITY_MASK)); }
    public void setIgnoreGravity(String value) { ignoreGravity=id(value);requestLayout(); }
    public String getIgnoreGravity() { return ignoreGravity; }
    public void setLayoutDirection(int value) {
        if(value!=0&&value!=1)throw new IllegalArgumentException("layoutDirection");
        direction=value;requestLayout();
    }
    public int getLayoutDirection() { return direction; }
    public void setMinWidth(int value) { minWidth=Math.max(0,value);requestLayout(); }
    public void setMinHeight(int value) { minHeight=Math.max(0,value);requestLayout(); }
    public int getMinWidth() { return minWidth; }
    public int getMinHeight() { return minHeight; }
    public void setMinimumWidth(int value) { setMinWidth(value); }
    public void setMinimumHeight(int value) { setMinHeight(value); }
    public int getMinimumWidth() { return minWidth; }
    public int getMinimumHeight() { return minHeight; }
    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(),attrs); }
    @Override public void addView(View child,int index,ViewGroup.LayoutParams params) {
        if(child==this)throw new IllegalArgumentException("A layout cannot contain itself");
        super.addView(child,index,params instanceof LayoutParams?params:params==null?new LayoutParams(-2,-2):new LayoutParams(params));
    }
    private static String id(String value) {
        if(value==null||value.isEmpty()||"@null".equals(value))return null;
        return value.startsWith("@+id/")?value.substring(5):value.startsWith("@id/")?value.substring(4):value;
    }
    private static int coord(long value) {
        if(value<=Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Layout coordinate overflow");
        return (int)value;
    }
    private static int exact(int size) { return MeasureSpec.makeMeasureSpec(Math.max(0,size),MeasureSpec.EXACTLY); }
    private void prepare() {
        nodes=new Node[getChildCount()];ids.clear();baselineView=null;
        for(int i=0;i<nodes.length;i++) {
            View child=getChildAt(i);
            if(!(child.getLayoutParams() instanceof LayoutParams))child.setLayoutParams(child.getLayoutParams()==null?new LayoutParams(-2,-2):new LayoutParams(child.getLayoutParams()));
            Node n=nodes[i]=new Node(child,(LayoutParams)child.getLayoutParams(),direction);
            if(n.p.width<-2||n.p.height<-2)throw new IllegalArgumentException("Invalid child dimension");
            String name=id(child.getId());
            if(name!=null&&ids.put(name,n)!=null)throw new IllegalArgumentException("Duplicate child ID: "+name);
        }
        horizontal=sort(0);vertical=sort(1);
    }
    // Each axis has its own graph: a horizontal dependency and an opposite vertical
    // dependency are legal. Kahn's algorithm also avoids stack overflow on long chains.
    private Node[] sort(int axis) {
        Map<Node,Set<Node>> outgoing=new IdentityHashMap<>();Map<Node,Integer> degree=new IdentityHashMap<>();
        for(Node n:nodes) { outgoing.put(n,new LinkedHashSet<Node>());degree.put(n,0); }
        int[] verbs=axis==0?new int[]{LEFT_OF,RIGHT_OF,ALIGN_LEFT,ALIGN_RIGHT}:new int[]{ABOVE,BELOW,ALIGN_TOP,ALIGN_BOTTOM,ALIGN_BASELINE};
        for(Node n:nodes)for(int verb:verbs) {
            Node anchor=ids.get(n.rules[verb]);
            if(anchor==n)throw new IllegalArgumentException("A child cannot anchor to itself: "+n.view.getId());
            if(anchor!=null&&outgoing.get(anchor).add(n))degree.put(n,degree.get(n)+1);
        }
        ArrayDeque<Node> ready=new ArrayDeque<>();for(Node n:nodes)if(degree.get(n)==0)ready.add(n);
        Node[] result=new Node[nodes.length];int count=0;
        while(!ready.isEmpty()) {
            Node n=ready.remove();result[count++]=n;
            for(Node next:outgoing.get(n)) { int d=degree.get(next)-1;degree.put(next,d);if(d==0)ready.add(next); }
        }
        if(count!=nodes.length)throw new IllegalArgumentException("Circular RelativeLayout dependency on "+(axis==0?"horizontal":"vertical")+" axis");
        return result;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        prepare();int wm=MeasureSpec.getMode(widthSpec),hm=MeasureSpec.getMode(heightSpec);
        // RTL needs a provisional right edge when the parent is unbounded.
        int w=wm==MeasureSpec.UNSPECIFIED?(direction==1?65536:-1):MeasureSpec.getSize(widthSpec);
        int h=hm==MeasureSpec.UNSPECIFIED?-1:MeasureSpec.getSize(heightSpec);
        solve(w,h,wm==MeasureSpec.UNSPECIFIED,hm==MeasureSpec.UNSPECIFIED,wm!=MeasureSpec.EXACTLY,hm!=MeasureSpec.EXACTLY);
        int desiredW=2*mPadding,desiredH=2*mPadding;
        for(Node n:nodes) {
            desiredW=Math.max(desiredW,coord(direction==1?(long)w-n.x+n.before[0]+mPadding:(long)n.x+n.view.getMeasuredWidth()+n.after[0]+mPadding));
            desiredH=Math.max(desiredH,coord((long)n.y+n.view.getMeasuredHeight()+n.after[1]+mPadding));
        }
        int measuredW=resolveSize(Math.max(Math.max(minWidth,getSuggestedMinimumWidth()),desiredW),widthSpec);
        int measuredH=resolveSize(Math.max(Math.max(minHeight,getSuggestedMinimumHeight()),desiredH),heightSpec);
        // Remeasure after wrap_content resolves: sibling anchors must follow centered
        // or parent-end anchored views, and width-dependent heights must be refreshed.
        solve(measuredW,measuredH,false,false,false,false);
        if(hm!=MeasureSpec.EXACTLY) {
            int need=2*mPadding;
            for(Node n:nodes)if(!n.parentEnd[1]&&!n.center[1])need=Math.max(need,coord((long)n.y+n.view.getMeasuredHeight()+n.after[1]+mPadding));
            int newH=resolveSize(Math.max(measuredH,need),heightSpec);
            if(newH!=measuredH) { measuredH=newH;solve(measuredW,measuredH,false,false,false,false); }
        }
        setMeasuredDimension(measuredW,measuredH);applyGravity(measuredW,measuredH);
        Node first=null;
        for(Node n:nodes)if(first==null||n.y<first.y||(n.y==first.y&&n.x<first.x))first=n;
        baselineView=first==null?null:first.view;
    }
    @Override public int getBaseline() { return baselineView==null?-1:baselineView.getBaseline(); }
    private void solve(int w,int h,boolean unboundedW,boolean unboundedH,boolean deferCenterW,boolean deferCenterH) {
        for(Node n:horizontal) {
            bounds(n,0,w);int ws=childSpec(n,0,w,unboundedW);
            int hs=initialSpec(n.p.height,h,n.before[1]+n.after[1],unboundedH);
            n.view.measure(ws,hs);position(n,0,w,deferCenterW);
        }
        for(Node n:vertical) {
            bounds(n,1,h);Node anchor=ids.get(n.rules[ALIGN_BASELINE]);
            boolean baseline=anchor!=null&&anchor.view.getBaseline()!=-1;
            if(baseline) { n.low[1]=coord((long)anchor.y+anchor.view.getBaseline()-Math.max(0,n.view.getBaseline()));n.high[1]=UNSET; }
            n.view.measure(exact(n.view.getMeasuredWidth()),childSpec(n,1,h,unboundedH));
            if(baseline)n.low[1]=coord((long)anchor.y+anchor.view.getBaseline()-Math.max(0,n.view.getBaseline()));
            position(n,1,h,deferCenterH);
        }
    }
    private int initialSpec(int dimension,int size,int margins,boolean unbounded) {
        if(unbounded||size<0)return dimension>=0?exact(dimension):MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        int available=Math.max(0,size-2*mPadding-margins);
        return dimension>=0?exact(Math.min(dimension,available)):MeasureSpec.makeMeasureSpec(available,dimension==-1?MeasureSpec.EXACTLY:MeasureSpec.AT_MOST);
    }
    private int childSpec(Node n,int axis,int size,boolean unbounded) {
        int lo=n.low[axis],hi=n.high[axis],dimension=axis==0?n.p.width:n.p.height;
        if(lo!=UNSET&&hi!=UNSET)return exact(coord(Math.max(0L,(long)hi-lo)));
        if(unbounded||size<0)return dimension>=0?exact(dimension):MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        int available=coord((long)(hi==UNSET?size-mPadding-n.after[axis]:hi)-(lo==UNSET?mPadding+n.before[axis]:lo));
        if(dimension>=0)return exact(available>=0?Math.min(dimension,available):dimension);
        if(dimension==-1)return exact(Math.max(0,available));
        return available>=0?MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST):MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
    }
    private void bounds(Node n,int axis,int size) {
        n.low[axis]=n.high[axis]=UNSET;n.parentEnd[axis]=false;
        int before=axis==0?LEFT_OF:ABOVE,after=axis==0?RIGHT_OF:BELOW;
        int alignBefore=axis==0?ALIGN_LEFT:ALIGN_TOP,alignAfter=axis==0?ALIGN_RIGHT:ALIGN_BOTTOM;
        edge(n,axis,size,before,false,true);edge(n,axis,size,after,true,true);
        edge(n,axis,size,alignBefore,true,false);edge(n,axis,size,alignAfter,false,false);
        if(n.rules[axis==0?ALIGN_PARENT_LEFT:ALIGN_PARENT_TOP]!=null)n.low[axis]=coord((long)mPadding+n.before[axis]);
        if(n.rules[axis==0?ALIGN_PARENT_RIGHT:ALIGN_PARENT_BOTTOM]!=null&&size>=0) { n.high[axis]=coord((long)size-mPadding-n.after[axis]);n.parentEnd[axis]=true; }
    }
    private void edge(Node n,int axis,int size,int verb,boolean low,boolean adjacent) {
        if(n.rules[verb]==null)return;
        Node a=ids.get(n.rules[verb]);int result;
        if(a==null) {
            if(!n.p.alignWithParent)return;
            if(!low&&size<0)return;
            result=low?coord((long)mPadding+n.before[axis]):coord((long)size-mPadding-n.after[axis]);
            if(!low)n.parentEnd[axis]=true;
        } else {
            int pos=axis==0?a.x:a.y,length=axis==0?a.view.getMeasuredWidth():a.view.getMeasuredHeight();
            if(low)result=coord((long)pos+(adjacent?(long)length+a.after[axis]:0)+n.before[axis]);
            else result=coord((long)pos+(adjacent?-a.before[axis]:length)-n.after[axis]);
        }
        if(low)n.low[axis]=result;else n.high[axis]=result;
    }
    private void position(Node n,int axis,int size,boolean deferCenter) {
        int length=axis==0?n.view.getMeasuredWidth():n.view.getMeasuredHeight(),pos;
        n.center[axis]=false;
        if(n.low[axis]!=UNSET)pos=n.low[axis];
        else if(n.high[axis]!=UNSET)pos=coord((long)n.high[axis]-length);
        else if(!deferCenter&&size>=0&&(n.rules[CENTER_IN_PARENT]!=null||n.rules[axis==0?CENTER_HORIZONTAL:CENTER_VERTICAL]!=null)) {
            pos=(size-length)/2;n.center[axis]=true;
        } else if(axis==0&&direction==1&&size>=0)pos=coord((long)size-mPadding-n.after[axis]-length);
        else pos=coord((long)mPadding+n.before[axis]);
        if(axis==0)n.x=pos;else n.y=pos;
    }
    private void applyGravity(int w,int h) {
        int horizontal=gravity&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK,vertical=gravity&Gravity.VERTICAL_GRAVITY_MASK;
        boolean moveX=horizontal!=Gravity.START&&horizontal!=0,moveY=vertical!=Gravity.TOP&&vertical!=0;
        if(!moveX&&!moveY)return;
        int left=Integer.MAX_VALUE,top=left,right=Integer.MIN_VALUE,bottom=right;
        for(Node n:nodes) {
            // Preserve RelativeLayout's axis-dependent ignoreGravity bounds.
            if(n.view!=idsView(ignoreGravity)||moveY) {
                left=Math.min(left,coord((long)n.x-n.before[0]));top=Math.min(top,coord((long)n.y-n.before[1]));
            }
            if(n.view!=idsView(ignoreGravity)||moveX) {
                right=Math.max(right,coord((long)n.x+n.view.getMeasuredWidth()+n.after[0]));bottom=Math.max(bottom,coord((long)n.y+n.view.getMeasuredHeight()+n.after[1]));
            }
        }
        if(left==Integer.MAX_VALUE||right==Integer.MIN_VALUE)return;
        int absolute=Gravity.getAbsoluteGravity(gravity,direction)&Gravity.HORIZONTAL_GRAVITY_MASK;
        long contentW=(long)right-left,contentH=(long)bottom-top;
        long targetX=absolute==Gravity.RIGHT?w-mPadding-contentW:absolute==Gravity.CENTER_HORIZONTAL?mPadding+(w-2L*mPadding-contentW)/2:mPadding;
        long targetY=vertical==Gravity.BOTTOM?h-mPadding-contentH:vertical==Gravity.CENTER_VERTICAL?mPadding+(h-2L*mPadding-contentH)/2:mPadding;
        if((gravity&Gravity.CLIP_HORIZONTAL)!=0)targetX=Math.max(mPadding,targetX);
        if((gravity&Gravity.CLIP_VERTICAL)!=0)targetY=Math.max(mPadding,targetY);
        int dx=moveX?coord(targetX-left):0,dy=moveY?coord(targetY-top):0;
        for(Node n:nodes)if(n.view!=idsView(ignoreGravity)) { n.x=coord((long)n.x+dx);n.y=coord((long)n.y+dy); }
    }
    private View idsView(String id) { Node n=ids.get(id);return n==null?null:n.view; }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        if(nodes.length!=getChildCount()||r-l!=getMeasuredWidth()||b-t!=getMeasuredHeight())measure(exact(r-l),exact(b-t));
        for(Node n:nodes)n.view.layout(n.x,n.y,coord((long)n.x+n.view.getMeasuredWidth()),coord((long)n.y+n.view.getMeasuredHeight()));
    }
    private static int parseGravity(String value) {
        try { return Integer.decode(value); }catch(NumberFormatException ignored) {}
        int result=0;
        for(String token:value.split("\\|")) {
            switch(token.trim()) {
                case "left":result|=Gravity.LEFT;break;case "right":result|=Gravity.RIGHT;break;
                case "start":result|=Gravity.START;break;case "end":result|=Gravity.END;break;
                case "top":result|=Gravity.TOP;break;case "bottom":result|=Gravity.BOTTOM;break;
                case "center":result|=Gravity.CENTER;break;case "center_horizontal":result|=Gravity.CENTER_HORIZONTAL;break;
                case "center_vertical":result|=Gravity.CENTER_VERTICAL;break;case "fill":result|=Gravity.FILL;break;
                case "fill_horizontal":result|=Gravity.FILL_HORIZONTAL;break;case "fill_vertical":result|=Gravity.FILL_VERTICAL;break;
                case "clip_horizontal":result|=Gravity.CLIP_HORIZONTAL;break;case "clip_vertical":result|=Gravity.CLIP_VERTICAL;break;
                case "no_gravity":break;default:throw new IllegalArgumentException("gravity: "+value);
            }
        }
        return result;
    }
    private static final class Node {
        final View view;final LayoutParams p;final String[] rules;
        final int[] low={UNSET,UNSET},high={UNSET,UNSET},before,after;
        final boolean[] parentEnd=new boolean[2],center=new boolean[2];int x,y;
        Node(View view,LayoutParams p,int direction) {
            this.view=view;this.p=p;rules=p.getRules(direction);
            before=new int[]{p.marginLeft(direction),p.topMargin};after=new int[]{p.marginRight(direction),p.bottomMargin};
        }
    }

    /** IDs are strings in this framework. Enabled boolean rules are returned as "-1". */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int leftMargin,topMargin,rightMargin,bottomMargin;
        public int startMargin=UNSET,endMargin=UNSET;
        public boolean alignWithParent;
        private final String[] rules=new String[22];
        private static final String[] ATTRS={"layout_toLeftOf","layout_toRightOf","layout_above","layout_below",
            "layout_alignBaseline","layout_alignLeft","layout_alignTop","layout_alignRight","layout_alignBottom",
            "layout_alignParentLeft","layout_alignParentTop","layout_alignParentRight","layout_alignParentBottom",
            "layout_centerInParent","layout_centerHorizontal","layout_centerVertical","layout_toStartOf","layout_toEndOf",
            "layout_alignStart","layout_alignEnd","layout_alignParentStart","layout_alignParentEnd"};
        public LayoutParams(int width,int height) { super(width,height); }
        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if(source instanceof LayoutParams) {
                LayoutParams p=(LayoutParams)source;
                leftMargin=p.leftMargin;topMargin=p.topMargin;rightMargin=p.rightMargin;bottomMargin=p.bottomMargin;
                startMargin=p.startMargin;endMargin=p.endMargin;alignWithParent=p.alignWithParent;
                System.arraycopy(p.rules,0,rules,0,rules.length);
            }
        }
        public LayoutParams(LayoutParams source) { this((ViewGroup.LayoutParams)source); }
        public LayoutParams(Context c,AttributeSet a) {
            super(c,a);if(a==null)return;
            int all=dimension(a,"layout_margin",0),horizontal=dimension(a,"layout_marginHorizontal",all),vertical=dimension(a,"layout_marginVertical",all);
            leftMargin=dimension(a,"layout_marginLeft",horizontal);rightMargin=dimension(a,"layout_marginRight",horizontal);
            topMargin=dimension(a,"layout_marginTop",vertical);bottomMargin=dimension(a,"layout_marginBottom",vertical);
            startMargin=dimension(a,"layout_marginStart",UNSET);endMargin=dimension(a,"layout_marginEnd",UNSET);
            alignWithParent=bool(a.get("layout_alignWithParentIfMissing"));
            for(int i=0;i<rules.length;i++)if(a.get(ATTRS[i])!=null) {
                if(isBoolean(i)) { if(bool(a.get(ATTRS[i])))addRule(i); }
                else addRule(i,a.get(ATTRS[i]));
            }
        }
        private static boolean isBoolean(int verb) { return verb>=ALIGN_PARENT_LEFT&&verb<=CENTER_VERTICAL||verb>=ALIGN_PARENT_START; }
        private static void valid(int verb) { if(verb<0||verb>=22)throw new IllegalArgumentException("Unknown relative rule: "+verb); }
        public void addRule(int verb) { addRule(verb,TRUE); }
        public void addRule(int verb,int value) {
            valid(verb);
            if(value==0) { removeRule(verb);return; }
            if(!isBoolean(verb)||value!=TRUE)throw new IllegalArgumentException("Use a string ID for sibling rules; booleans use TRUE or 0");
            rules[verb]=ENABLED;
        }
        public void addRule(int verb,String anchor) {
            valid(verb);
            if(anchor==null||anchor.isEmpty()||"@null".equals(anchor)) { removeRule(verb);return; }
            if(isBoolean(verb)) {
                if(ENABLED.equals(anchor)||bool(anchor))rules[verb]=ENABLED;else removeRule(verb);
            } else {
                String name=id(anchor);
                if(name==null||name.isEmpty())throw new IllegalArgumentException("Empty anchor ID");
                rules[verb]=name;
            }
        }
        public void removeRule(int verb) { valid(verb);rules[verb]=null; }
        public String getRule(int verb) { valid(verb);return rules[verb]; }
        public String[] getRules() { return rules.clone(); }
        /** Returns a resolved copy; logical rules override the corresponding physical pair. */
        public String[] getRules(int direction) {
            if(direction!=0&&direction!=1)throw new IllegalArgumentException("layoutDirection");
            String[] result=getRules();boolean rtl=direction==1;
            resolve(result,START_OF,END_OF,LEFT_OF,RIGHT_OF,rtl);
            resolve(result,ALIGN_START,ALIGN_END,ALIGN_LEFT,ALIGN_RIGHT,rtl);
            resolve(result,ALIGN_PARENT_START,ALIGN_PARENT_END,ALIGN_PARENT_LEFT,ALIGN_PARENT_RIGHT,rtl);
            return result;
        }
        private static void resolve(String[] r,int start,int end,int left,int right,boolean rtl) {
            if(r[start]!=null||r[end]!=null) {
                r[left]=r[right]=null;r[rtl?right:left]=r[start];r[rtl?left:right]=r[end];
            }
            r[start]=r[end]=null;
        }
        public void setMargins(int left,int top,int right,int bottom) { leftMargin=left;topMargin=top;rightMargin=right;bottomMargin=bottom; }
        public void setMarginStart(int value) { startMargin=value; }
        public void setMarginEnd(int value) { endMargin=value; }
        public int getMarginStart() { return startMargin==UNSET?leftMargin:startMargin; }
        public int getMarginEnd() { return endMargin==UNSET?rightMargin:endMargin; }
        public boolean isMarginRelative() { return startMargin!=UNSET||endMargin!=UNSET; }
        private int marginLeft(int direction) { int relative=direction==1?endMargin:startMargin;return relative==UNSET?leftMargin:relative; }
        private int marginRight(int direction) { int relative=direction==1?startMargin:endMargin;return relative==UNSET?rightMargin:relative; }
        private static int dimension(AttributeSet a,String name,int fallback) {
            String raw=a.get(name);if(raw==null)return fallback;
            double value=Double.parseDouble(raw.replaceAll("(dp|dip|sp|px)$",""));
            if(!Double.isFinite(value)||value<=Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException(name+": "+raw);
            return coord(Math.round(value));
        }
        private static boolean bool(String value) {
            if(value==null||"false".equals(value)||"0".equals(value))return false;
            if("true".equals(value)||"1".equals(value))return true;
            throw new IllegalArgumentException("Expected boolean: "+value);
        }
    }
}
