package com.android.widget;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.view.View;
import com.android.view.ViewGroup;
import java.util.*;

/** Constraint-based layout; compatibility is described in docs/constraintlayout.md. */
public class ConstraintLayout extends ViewGroup {
    public static final String PARENT_ID = "parent";
    public static final int LAYOUT_DIRECTION_LTR = 0, LAYOUT_DIRECTION_RTL = 1;
    private int direction, minWidth, minHeight, maxWidth = Integer.MAX_VALUE, maxHeight = Integer.MAX_VALUE;
    private Node[] nodes = new Node[0];
    public ConstraintLayout(Context c) { this(c, null); }
    public ConstraintLayout(Context c, AttributeSet a) { this(c, a, 0); }
    public ConstraintLayout(Context c, AttributeSet a, int style) { this(c, a, style, 0); }
    public ConstraintLayout(Context c, AttributeSet a, int style, int res) {
        super(c, a, style, res);
        if (a != null) {
            minWidth=a.dimension("minWidth",0); minHeight=a.dimension("minHeight",0);
            maxWidth=a.dimension("maxWidth",maxWidth); maxHeight=a.dimension("maxHeight",maxHeight);
            String d=a.get("layoutDirection");
            if(d!=null) { if(!"ltr".equals(d)&&!"rtl".equals(d))throw new IllegalArgumentException("layoutDirection: "+d); direction="rtl".equals(d)?1:0; }
        }
    }
    public void setLayoutDirection(int value) { if(value!=0&&value!=1)throw new IllegalArgumentException("layoutDirection"); direction=value;requestLayout(); }
    public int getLayoutDirection() { return direction; }
    public void setMinWidth(int value) { minWidth=Math.max(0,value);requestLayout(); }
    public void setMinHeight(int value) { minHeight=Math.max(0,value);requestLayout(); }
    public void setMaxWidth(int value) { maxWidth=Math.max(0,value);requestLayout(); }
    public void setMaxHeight(int value) { maxHeight=Math.max(0,value);requestLayout(); }
    public int getMinWidth() { return minWidth; }
    public int getMinHeight() { return minHeight; }
    public int getMaxWidth() { return maxWidth; }
    public int getMaxHeight() { return maxHeight; }
    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(),attrs); }
    @Override public void addView(View child,int index,ViewGroup.LayoutParams params) {
        if(child==this)throw new IllegalArgumentException("A layout cannot contain itself");
        super.addView(child,index,params instanceof LayoutParams?params:params==null?new LayoutParams(-2,-2):new LayoutParams(params));
    }
    private static int pixel(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite layout result");
        long rounded=Math.round(value);
        if(rounded<Integer.MIN_VALUE||rounded>Integer.MAX_VALUE)throw new IllegalArgumentException("Layout coordinate exceeds integer range");
        return (int)rounded;
    }
    private int inner(int size) { return Math.max(0,size-2*mPadding); }
    private static int limited(int desired,int min,int max,int spec) {
        return MeasureSpec.getMode(spec)==MeasureSpec.EXACTLY?MeasureSpec.getSize(spec):resolveSize(Math.min(max,Math.max(min,desired)),spec);
    }
    private void prepare(int widthSpec,int heightSpec) {
        nodes=new Node[getChildCount()];Map<String,Integer> ids=new HashMap<>();
        for(int i=0;i<nodes.length;i++) {
            View child=getChildAt(i);
            if(!(child.getLayoutParams() instanceof LayoutParams))child.setLayoutParams(new LayoutParams(child.getLayoutParams()));
            Node n=nodes[i]=new Node(child,(LayoutParams)child.getLayoutParams());String id=child.getId();
            if(id!=null&&(PARENT_ID.equals(id)||ids.put(id,i)!=null))throw new IllegalArgumentException("Duplicate/reserved child ID: "+id);
            child.measure(initialSpec(widthSpec,n.p.width,n.before[0]+n.after[0]),initialSpec(heightSpec,n.p.height,n.before[1]+n.after[1]));
            n.wrap[0]=child.getMeasuredWidth();n.wrap[1]=child.getMeasuredHeight();n.baseline=child.getBaseline();
        }
        for(int i=0;i<nodes.length;i++)nodes[i].connect(ids,i);
    }
    private int initialSpec(int spec,int dimension,int margins) {
        if(dimension>0)return MeasureSpec.makeMeasureSpec(dimension,MeasureSpec.EXACTLY);
        return MeasureSpec.getMode(spec)==MeasureSpec.UNSPECIFIED?MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)
                :MeasureSpec.makeMeasureSpec(Math.max(0,inner(MeasureSpec.getSize(spec))-margins),MeasureSpec.AT_MOST);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        prepare(widthSpec,heightSpec);int seedW=2*mPadding,seedH=seedW;
        for(Node n:nodes)if(!n.guide) {
            seedW=Math.max(seedW,n.wrap[0]+n.before[0]+n.after[0]+2*mPadding);
            seedH=Math.max(seedH,n.wrap[1]+n.before[1]+n.after[1]+2*mPadding);
        }
        for(int a=0;a<2;a++)for(List<Integer> chain:chains(a)) {
            int length=2*mPadding;
            for(int i:chain)length+=nodes[i].wrap[a]+nodes[i].before[a]+nodes[i].after[a];
            if(a==0)seedW=Math.max(seedW,length);else seedH=Math.max(seedH,length);
        }
        int w=limited(seedW,minWidth,maxWidth,widthSpec),h=limited(seedH,minHeight,maxHeight,heightSpec);
        double previousW=-1,previousH=-1,previousNeedW=0,previousNeedH=0;
        for(int pass=0;pass<64;pass++) {
            solve(w,h);int needW=2*mPadding,needH=needW;
            for(Node n:nodes)if(!n.guide) {
                needW=Math.max(needW,pixel(n.pos[0]+n.size[0]+n.after[0]+mPadding));
                needH=Math.max(needH,pixel(n.pos[1]+n.size[1]+n.after[1]+mPadding));
                if(n.pos[0]<mPadding)needW=Math.max(needW,w+pixel(mPadding-n.pos[0]));
                if(n.pos[1]<mPadding)needH=Math.max(needH,h+pixel(mPadding-n.pos[1]));
            }
            int nextW=limited(grow(w,needW,previousW,previousNeedW),minWidth,maxWidth,widthSpec);
            int nextH=limited(grow(h,needH,previousH,previousNeedH),minHeight,maxHeight,heightSpec);
            if(nextW==w&&nextH==h) { setMeasuredDimension(w,h);return; }
            previousW=w;previousH=h;previousNeedW=needW;previousNeedH=needH;w=nextW;h=nextH;
        }
        throw new IllegalArgumentException("wrap_content constraints do not converge; constrain the parent size");
    }
    private static int grow(int size,int need,double previous,double previousNeed) {
        if(need<=size)return size;
        double slope=previous>=0&&size!=previous?(need-previousNeed)/(size-previous):0;
        return pixel(Math.ceil(size+(need-size)/(slope>0&&slope<.999999?1-slope:1)));
    }
    // Solve linear positions/sizes, activate min/max or ratio bounds, then remeasure wrapping content.
    private void solve(int w,int h) {
        for(Node n:nodes)Arrays.fill(n.forced,Double.NaN);
        for(int pass=0;pass<24;pass++) {
            axis(0,w);axis(1,h);boolean changed=false;
            for(Node n:nodes)if(!n.guide) {
                double[] desired={n.size[0],n.size[1]};
                if(n.ratio>0) {
                    boolean rw=n.p.width==0,rh=n.p.height==0;
                    if(rw&&rh) {
                        boolean horizontal=n.start[0]!=null&&n.end[0]!=null,vertical=n.start[1]!=null&&n.end[1]!=null;
                        double width=horizontal&&!vertical?desired[0]:vertical&&!horizontal?desired[1]*n.ratio:Math.min(desired[0],desired[1]*n.ratio);
                        double minimum=Math.max(n.minimum(0),n.minimum(1)*n.ratio);
                        double maximum=Math.min(n.maximum(0),n.maximum(1)*n.ratio);
                        if(minimum>maximum)throw new IllegalArgumentException("Ratio conflicts with min/max dimensions");
                        desired[0]=Math.max(minimum,Math.min(maximum,Math.max(0,width)));desired[1]=desired[0]/n.ratio;
                    }
                    else if(rw) {
                        desired[0]=desired[1]*n.ratio;
                        if(desired[0]<n.minimum(0)||desired[0]>n.maximum(0))throw new IllegalArgumentException("Ratio conflicts with fixed height and width bounds");
                    } else if(rh) {
                        desired[1]=desired[0]/n.ratio;
                        if(desired[1]<n.minimum(1)||desired[1]>n.maximum(1))throw new IllegalArgumentException("Ratio conflicts with fixed width and height bounds");
                    }
                }
                for(int a=0;a<2;a++)if(n.dimension(a)==0||n.dimension(a)==-2&&n.constrained(a)) {
                    if((n.dimension(a)==-2&&n.constrained(a)||n.dimension(a)==0&&n.defaultMode(a)==1)&&n.start[a]!=null&&n.end[a]!=null) {
                        double available=anchorPosition(n.end[a],a,a==0?w:h)-n.after[a]-anchorPosition(n.start[a],a,a==0?w:h)-n.before[a];
                        desired[a]=Math.min(n.wrap[a],Math.max(0,available));
                    }
                    if(n.minimum(a)>n.maximum(a))throw new IllegalArgumentException("Minimum size exceeds maximum size");
                    double value=Math.max(n.minimum(a),Math.min(n.maximum(a),Math.max(0,desired[a])));
                    if(Math.abs(value-n.size[a])>.001) { n.forced[a]=value;changed=true; }
                }
            }
            for(Node n:nodes)if(!n.guide) {
                int childW=Math.max(0,pixel(n.size[0])),childH=Math.max(0,pixel(n.size[1]));
                n.view.measure(MeasureSpec.makeMeasureSpec(childW,MeasureSpec.EXACTLY),n.p.height==-2?MeasureSpec.makeMeasureSpec(inner(h),MeasureSpec.AT_MOST):MeasureSpec.makeMeasureSpec(childH,MeasureSpec.EXACTLY));
                if(n.p.height==-2&&n.wrap[1]!=n.view.getMeasuredHeight()) { n.wrap[1]=n.view.getMeasuredHeight();changed=true; }
                int baseline=n.view.getBaseline();if(baseline!=n.baseline) { n.baseline=baseline;changed=true; }
            }
            if(!changed) {
                for(Node n:nodes)n.view.measure(MeasureSpec.makeMeasureSpec(Math.max(0,pixel(n.size[0])),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,pixel(n.size[1])),MeasureSpec.EXACTLY));
                return;
            }
        }
        throw new IllegalArgumentException("Conflicting size/ratio constraints");
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        if(r-l!=getMeasuredWidth()||b-t!=getMeasuredHeight())solve(r-l,b-t);
        for(Node n:nodes) { int x=pixel(n.pos[0]),y=pixel(n.pos[1]);n.view.layout(x,y,pixel((double)x+n.view.getMeasuredWidth()),pixel((double)y+n.view.getMeasuredHeight())); }
    }
    private final class Node {
        final View view;final LayoutParams p;final boolean guide;
        final int[] before=new int[2],after=new int[2],wrap=new int[2];
        final double[] pos=new double[2],size=new double[2],forced={Double.NaN,Double.NaN};
        final Anchor[] start=new Anchor[2],end=new Anchor[2];
        double ratio;int baseline;boolean relative;int baselineTarget=-1,circleTarget=-2;
        Node(View v,LayoutParams params) {
            view=v;p=params;p.validate();guide=v instanceof Guideline||p.guideBegin>=0||p.guideEnd>=0||p.guidePercent>=0;
            before[0]=p.leftMargin;after[0]=p.rightMargin;before[1]=p.topMargin;after[1]=p.bottomMargin;
            if(p.startMargin!=Integer.MIN_VALUE) { if(direction==0)before[0]=p.startMargin;else after[0]=p.startMargin; }
            if(p.endMargin!=Integer.MIN_VALUE) { if(direction==0)after[0]=p.endMargin;else before[0]=p.endMargin; }
            ratio=parseRatio(p.dimensionRatio);baseline=v.getBaseline();
        }
        void connect(Map<String,Integer> ids,int self) {
            start[0]=anchor(p.leftToLeft,p.leftToRight,0,1,ids,self);end[0]=anchor(p.rightToLeft,p.rightToRight,0,1,ids,self);
            relative=p.startToStart!=null||p.startToEnd!=null||p.endToStart!=null||p.endToEnd!=null;
            Anchor s=anchor(p.startToStart,p.startToEnd,direction==0?0:1,direction==0?1:0,ids,self);
            Anchor e=anchor(p.endToStart,p.endToEnd,direction==0?0:1,direction==0?1:0,ids,self);
            if(s!=null) { if(direction==0)start[0]=s;else end[0]=s; }
            if(e!=null) { if(direction==0)end[0]=e;else start[0]=e; }
            start[1]=anchor(p.topToTop,p.topToBottom,0,1,ids,self);end[1]=anchor(p.bottomToTop,p.bottomToBottom,0,1,ids,self);
            if(p.baselineToBaseline!=null) { baselineTarget=target(p.baselineToBaseline,ids,self);if(baselineTarget<0)throw new IllegalArgumentException("Parent has no baseline anchor"); }
            if(p.circleConstraint!=null)circleTarget=target(p.circleConstraint,ids,self);
            for(int a=0;a<2;a++)if(dimension(a)==-1) { start[a]=new Anchor(-1,0);end[a]=new Anchor(-1,1); }
        }
        int dimension(int a) { return a==0?p.width:p.height; }
        boolean constrained(int a) { return a==0?p.constrainedWidth:p.constrainedHeight; }
        double bias(int a) { double b=a==0?p.horizontalBias:p.verticalBias;return a==0&&direction==1&&relative?1-b:b; }
        int style(int a) { return a==0?p.horizontalChainStyle:p.verticalChainStyle; }
        float weight(int a) { return a==0?p.horizontalWeight:p.verticalWeight; }
        double minimum(int a) { int v=a==0?p.matchConstraintMinWidth:p.matchConstraintMinHeight;return v==-2?wrap[a]:v; }
        double maximum(int a) { int v=a==0?p.matchConstraintMaxWidth:p.matchConstraintMaxHeight;return v==-2?wrap[a]:v==0?Double.MAX_VALUE:v; }
        boolean flexible(int a) { return !guide&&dimension(a)==0&&Double.isNaN(forced[a])&&percent(a)<0&&defaultMode(a)==0; }
        int defaultMode(int a) { return a==0?p.matchConstraintDefaultWidth:p.matchConstraintDefaultHeight; }
        float percent(int a) { return a==0?p.matchConstraintPercentWidth:p.matchConstraintPercentHeight; }
    }
    private static final class Anchor { final int node,edge;Anchor(int n,int e) { node=n;edge=e; } }
    private static int target(String value,Map<String,Integer> ids,int self) {
        String id=normalize(value);if(PARENT_ID.equals(id))return -1;Integer found=ids.get(id);
        if(found==null||found==self)throw new IllegalArgumentException("Missing/self constraint target: "+value);return found;
    }
    private static Anchor anchor(String first,String second,int firstEdge,int secondEdge,Map<String,Integer> ids,int self) {
        return first!=null?new Anchor(target(first,ids,self),firstEdge):second!=null?new Anchor(target(second,ids,self),secondEdge):null;
    }
    // Rows represent sum(coefficients * variables) + constant == 0; variables are x,width pairs.
    private double[] expression() { return new double[nodes.length*2+1]; }
    private double[] variable(int i,boolean size) { double[] row=expression();row[2*i+(size?1:0)]=1;return row; }
    private double[] constant(double value) { double[] row=expression();row[row.length-1]=value;return row; }
    private static double[] plus(double[] a,double[] b,double factor) { for(int i=0;i<a.length;i++)a[i]+=b[i]*factor;return a; }
    private double[] edge(Anchor anchor,int parentSize) {
        if(anchor.node<0)return constant(anchor.edge==0?mPadding:parentSize-mPadding);
        return plus(variable(anchor.node,false),variable(anchor.node,true),anchor.edge);
    }
    private double[] boundary(Node n,int a,boolean finish,int parentSize) {
        return plus(edge(finish?n.end[a]:n.start[a],parentSize),constant(finish?-n.after[a]:n.before[a]),1);
    }
    private double anchorPosition(Anchor anchor,int a,int parentSize) {
        return anchor.node<0?(anchor.edge==0?mPadding:parentSize-mPadding):nodes[anchor.node].pos[a]+anchor.edge*nodes[anchor.node].size[a];
    }
    private void axis(int a,int parentSize) {
        int count=nodes.length;if(count==0)return;
        List<List<Integer>> chains=chains(a);boolean[] chained=new boolean[count];
        for(List<Integer> chain:chains)for(int i:chain)chained[i]=true;
        List<double[]> rows=new ArrayList<>();
        for(int i=0;i<count;i++) {
            Node n=nodes[i];int dimension=n.dimension(a);
            if(n.guide) {
                rows.add(variable(i,true));double position=mPadding;
                if((n.p.orientation==LayoutParams.VERTICAL?0:1)==a)position+=n.p.guideBegin>=0?n.p.guideBegin:n.p.guideEnd>=0?inner(parentSize)-n.p.guideEnd:Math.max(0,n.p.guidePercent)*inner(parentSize);
                rows.add(plus(variable(i,false),constant(position),-1));continue;
            }
            boolean stretch=n.flexible(a)&&(chained[i]||n.start[a]!=null&&n.end[a]!=null)&&n.circleTarget < -1&&!(a==1&&n.baselineTarget>=0);
            double fixed=dimension>0?dimension:n.wrap[a];
            if(!Double.isNaN(n.forced[a]))fixed=n.forced[a];
            else if(dimension==-1) { fixed=Math.max(0,inner(parentSize)-n.before[a]-n.after[a]);stretch=false; }
            else if(dimension==0&&(n.percent(a)>=0||n.defaultMode(a)==2)) { fixed=inner(parentSize)*(n.percent(a)<0?1:n.percent(a));stretch=false; }
            if(chained[i]&&n.flexible(a)&&n.weight(a)==0) { stretch=false;fixed=0; }
            if(!stretch)rows.add(plus(variable(i,true),constant(Math.max(0,fixed)),-1));
            if(chained[i])continue;
            if(n.circleTarget>=-1) {
                double[] center=n.circleTarget<0?constant(parentSize/2.0):plus(variable(n.circleTarget,false),variable(n.circleTarget,true),.5);
                double angle=Math.toRadians(n.p.circleAngle);plus(center,constant((a==0?Math.sin(angle):-Math.cos(angle))*n.p.circleRadius),1);
                rows.add(plus(plus(variable(i,false),variable(i,true),.5),center,-1));
            } else if(a==1&&n.baselineTarget>=0) {
                if(n.baseline<0||nodes[n.baselineTarget].baseline<0)throw new IllegalArgumentException("Baseline constraint needs baseline-capable views");
                rows.add(plus(plus(variable(i,false),variable(n.baselineTarget,false),-1),constant(n.baseline-nodes[n.baselineTarget].baseline),1));
            } else if(n.start[a]!=null&&n.end[a]!=null) {
                if(stretch) {
                    rows.add(plus(variable(i,false),boundary(n,a,false,parentSize),-1));
                    rows.add(plus(plus(variable(i,false),variable(i,true),1),boundary(n,a,true,parentSize),-1));
                } else {
                    double bias=n.bias(a);
                    rows.add(plus(plus(plus(variable(i,false),variable(i,true),bias),boundary(n,a,false,parentSize),bias-1),boundary(n,a,true,parentSize),-bias));
                }
            } else if(n.start[a]!=null)rows.add(plus(variable(i,false),boundary(n,a,false,parentSize),-1));
            else if(n.end[a]!=null)rows.add(plus(plus(variable(i,false),variable(i,true),1),boundary(n,a,true,parentSize),-1));
            else rows.add(plus(variable(i,false),constant(mPadding+n.before[a]),-1));
        }
        for(List<Integer> chain:chains)chainRows(rows,chain,a,parentSize);
        double[] result=eliminate(rows,count*2);
        for(int i=0;i<count;i++) { nodes[i].pos[a]=result[2*i];nodes[i].size[a]=result[2*i+1]; }
    }
    private int previous(int i,int a) {
        Anchor left=nodes[i].start[a];if(left==null||left.node<0||left.edge!=1||nodes[left.node].guide)return -1;
        Anchor back=nodes[left.node].end[a];return back!=null&&back.node==i&&back.edge==0?left.node:-1;
    }
    private int next(int i,int a) {
        Anchor right=nodes[i].end[a];if(right==null||right.node<0||right.edge!=0||nodes[right.node].guide)return -1;
        Anchor back=nodes[right.node].start[a];return back!=null&&back.node==i&&back.edge==1?right.node:-1;
    }
    private List<List<Integer>> chains(int a) {
        List<List<Integer>> result=new ArrayList<>();boolean[] visited=new boolean[nodes.length];
        for(int i=0;i<nodes.length;i++)if(!nodes[i].guide&&previous(i,a)<0&&next(i,a)>=0) {
            List<Integer> chain=new ArrayList<>();int current=i;
            while(current>=0) {
                if(visited[current])throw new IllegalArgumentException("Cyclic chain");
                visited[current]=true;chain.add(current);current=next(current,a);
            }
            Node last=nodes[chain.get(chain.size()-1)];
            if(nodes[i].start[a]==null||last.end[a]==null)throw new IllegalArgumentException("Chain requires two outer anchors");
            result.add(chain);
        }
        return result;
    }
    // Chain styles constrain the gaps; flexible dimensions share remaining space by weight.
    private void chainRows(List<double[]> rows,List<Integer> chain,int a,int parentSize) {
        int first=chain.get(0),last=chain.get(chain.size()-1);List<double[]> gaps=new ArrayList<>();
        gaps.add(plus(variable(first,false),boundary(nodes[first],a,false,parentSize),-1));
        for(int j=1;j<chain.size();j++) {
            int i=chain.get(j),prev=chain.get(j-1);
            gaps.add(plus(plus(plus(variable(i,false),variable(prev,false),-1),variable(prev,true),-1),constant(nodes[prev].after[a]+nodes[i].before[a]),-1));
        }
        gaps.add(plus(plus(boundary(nodes[last],a,true,parentSize),variable(last,false),-1),variable(last,true),-1));
        List<Integer> flexible=new ArrayList<>();for(int i:chain)if(nodes[i].flexible(a)&&nodes[i].weight(a)!=0)flexible.add(i);
        if(!flexible.isEmpty()) {
            rows.addAll(gaps);int ref=-1;
            for(int i:flexible) {
                float weight=nodes[i].weight(a);if(weight==0) { rows.add(variable(i,true));continue; }
                if(ref<0)ref=i;else rows.add(plus(variable(i,true),variable(ref,true),-(weight<0?1:weight)/(nodes[ref].weight(a)<0?1:nodes[ref].weight(a))));
            }
            if(ref<0)throw new IllegalArgumentException("Chain weights cannot all be zero");
        } else {
            int head=a==0&&direction==1?last:first,style=nodes[head].style(a);
            if(style==LayoutParams.CHAIN_PACKED) {
                for(int j=1;j<gaps.size()-1;j++)rows.add(gaps.get(j));double bias=nodes[head].bias(a);
                rows.add(plus(plus(expression(),gaps.get(0),1-bias),gaps.get(gaps.size()-1),-bias));
            } else if(style==LayoutParams.CHAIN_SPREAD_INSIDE) {
                rows.add(gaps.get(0));rows.add(gaps.get(gaps.size()-1));
                for(int j=2;j<gaps.size()-1;j++)rows.add(plus(gaps.get(j).clone(),gaps.get(1),-1));
            } else for(int j=1;j<gaps.size();j++)rows.add(plus(gaps.get(j).clone(),gaps.get(0),-1));
        }
    }
    // Pivoted elimination rejects inconsistent or underdetermined graphs instead of hanging.
    private static double[] eliminate(List<double[]> rows,int variables) {
        double[][] matrix=rows.toArray(new double[0][]);int rank=0;int[] pivots=new int[Math.min(rows.size(),variables)];
        for(int col=0;col<variables&&rank<matrix.length;col++) {
            int best=rank;for(int r=rank+1;r<matrix.length;r++)if(Math.abs(matrix[r][col])>Math.abs(matrix[best][col]))best=r;
            if(Math.abs(matrix[best][col])<1e-9)continue;
            double[] swap=matrix[rank];matrix[rank]=matrix[best];matrix[best]=swap;
            double divisor=matrix[rank][col];for(int c=col;c<=variables;c++)matrix[rank][c]/=divisor;
            for(int r=0;r<matrix.length;r++)if(r!=rank) {
                double factor=matrix[r][col];if(Math.abs(factor)<1e-12)continue;
                for(int c=col;c<=variables;c++)matrix[r][c]-=factor*matrix[rank][c];
            }
            pivots[rank++]=col;
        }
        for(int r=rank;r<matrix.length;r++)if(Math.abs(matrix[r][variables])>.01)throw new IllegalArgumentException("Conflicting/cyclic constraints");
        if(rank<variables)throw new IllegalArgumentException("Underdetermined/cyclic constraints");
        double[] result=new double[variables];for(int r=0;r<rank;r++)result[pivots[r]]=-matrix[r][variables];return result;
    }
    private static String normalize(String value) {
        if(value==null)return null;
        return "0".equals(value)?PARENT_ID:value.startsWith("@+id/")?value.substring(5):value.startsWith("@id/")?value.substring(4):value;
    }
    private static double parseRatio(String value) {
        if(value==null||value.isEmpty())return 0;
        String ratio=value.trim();int comma=ratio.indexOf(',');
        if(comma>=0) {
            String side=ratio.substring(0,comma);
            if(!"W".equalsIgnoreCase(side)&&!"H".equalsIgnoreCase(side))throw new IllegalArgumentException("Invalid dimensionRatio: "+value);
            ratio=ratio.substring(comma+1);
        }
        int colon=ratio.indexOf(':');double result;
        if(colon<0)result=Double.parseDouble(ratio);
        else {
            double numerator=Double.parseDouble(ratio.substring(0,colon)),denominator=Double.parseDouble(ratio.substring(colon+1));
            if(numerator<=0||denominator<=0)throw new IllegalArgumentException("Ratio components must be positive");
            result=numerator/denominator;
        }
        if(!Double.isFinite(result)||result<=0)throw new IllegalArgumentException("Invalid dimensionRatio: "+value);return result;
    }
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public static final int MATCH_CONSTRAINT=0,HORIZONTAL=0,VERTICAL=1;
        public static final int MATCH_CONSTRAINT_SPREAD=0,MATCH_CONSTRAINT_WRAP=1,MATCH_CONSTRAINT_PERCENT=2;
        public static final int CHAIN_SPREAD=0,CHAIN_SPREAD_INSIDE=1,CHAIN_PACKED=2;
        public String leftToLeft,leftToRight,rightToLeft,rightToRight,topToTop,topToBottom,bottomToTop,bottomToBottom,baselineToBaseline;
        public String startToStart,startToEnd,endToStart,endToEnd,circleConstraint,dimensionRatio;
        public int leftMargin,rightMargin,topMargin,bottomMargin,startMargin=Integer.MIN_VALUE,endMargin=Integer.MIN_VALUE;
        public float horizontalBias=.5f,verticalBias=.5f,horizontalWeight=-1,verticalWeight=-1;
        public int horizontalChainStyle,verticalChainStyle,circleRadius;
        public float circleAngle;
        public int matchConstraintDefaultWidth,matchConstraintDefaultHeight;
        public int matchConstraintMinWidth,matchConstraintMinHeight,matchConstraintMaxWidth,matchConstraintMaxHeight;
        public float matchConstraintPercentWidth=-1,matchConstraintPercentHeight=-1;
        public boolean constrainedWidth,constrainedHeight;
        public int orientation=VERTICAL,guideBegin=-1,guideEnd=-1;
        public float guidePercent=-1;
        public LayoutParams(int width,int height) { super(width,height); }
        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if(source instanceof LayoutParams) {
                LayoutParams s=(LayoutParams)source;
                leftToLeft=s.leftToLeft;leftToRight=s.leftToRight;rightToLeft=s.rightToLeft;rightToRight=s.rightToRight;
                topToTop=s.topToTop;topToBottom=s.topToBottom;bottomToTop=s.bottomToTop;bottomToBottom=s.bottomToBottom;baselineToBaseline=s.baselineToBaseline;
                startToStart=s.startToStart;startToEnd=s.startToEnd;endToStart=s.endToStart;endToEnd=s.endToEnd;
                circleConstraint=s.circleConstraint;dimensionRatio=s.dimensionRatio;
                leftMargin=s.leftMargin;rightMargin=s.rightMargin;topMargin=s.topMargin;bottomMargin=s.bottomMargin;startMargin=s.startMargin;endMargin=s.endMargin;
                horizontalBias=s.horizontalBias;verticalBias=s.verticalBias;horizontalWeight=s.horizontalWeight;verticalWeight=s.verticalWeight;
                horizontalChainStyle=s.horizontalChainStyle;verticalChainStyle=s.verticalChainStyle;circleRadius=s.circleRadius;circleAngle=s.circleAngle;
                matchConstraintDefaultWidth=s.matchConstraintDefaultWidth;matchConstraintDefaultHeight=s.matchConstraintDefaultHeight;
                matchConstraintMinWidth=s.matchConstraintMinWidth;matchConstraintMinHeight=s.matchConstraintMinHeight;matchConstraintMaxWidth=s.matchConstraintMaxWidth;matchConstraintMaxHeight=s.matchConstraintMaxHeight;
                matchConstraintPercentWidth=s.matchConstraintPercentWidth;matchConstraintPercentHeight=s.matchConstraintPercentHeight;
                constrainedWidth=s.constrainedWidth;constrainedHeight=s.constrainedHeight;orientation=s.orientation;guideBegin=s.guideBegin;guideEnd=s.guideEnd;guidePercent=s.guidePercent;
            }
        }
        public LayoutParams(Context c,AttributeSet attrs) {
            super(c,attrs);if(attrs==null)return;
            leftToLeft=get(attrs,"layout_constraintLeft_toLeftOf");leftToRight=get(attrs,"layout_constraintLeft_toRightOf");
            rightToLeft=get(attrs,"layout_constraintRight_toLeftOf");rightToRight=get(attrs,"layout_constraintRight_toRightOf");
            topToTop=get(attrs,"layout_constraintTop_toTopOf");topToBottom=get(attrs,"layout_constraintTop_toBottomOf");
            bottomToTop=get(attrs,"layout_constraintBottom_toTopOf");bottomToBottom=get(attrs,"layout_constraintBottom_toBottomOf");
            baselineToBaseline=get(attrs,"layout_constraintBaseline_toBaselineOf");
            startToStart=get(attrs,"layout_constraintStart_toStartOf");startToEnd=get(attrs,"layout_constraintStart_toEndOf");
            endToStart=get(attrs,"layout_constraintEnd_toStartOf");endToEnd=get(attrs,"layout_constraintEnd_toEndOf");
            circleConstraint=get(attrs,"layout_constraintCircle");dimensionRatio=get(attrs,"layout_constraintDimensionRatio");
            int margin=dim(attrs,"layout_margin",0),horizontal=dim(attrs,"layout_marginHorizontal",margin),vertical=dim(attrs,"layout_marginVertical",margin);
            leftMargin=dim(attrs,"layout_marginLeft",horizontal);rightMargin=dim(attrs,"layout_marginRight",horizontal);
            topMargin=dim(attrs,"layout_marginTop",vertical);bottomMargin=dim(attrs,"layout_marginBottom",vertical);
            startMargin=dim(attrs,"layout_marginStart",Integer.MIN_VALUE);endMargin=dim(attrs,"layout_marginEnd",Integer.MIN_VALUE);
            horizontalBias=number(attrs,"layout_constraintHorizontal_bias",.5f);verticalBias=number(attrs,"layout_constraintVertical_bias",.5f);
            horizontalWeight=number(attrs,"layout_constraintHorizontal_weight",-1);verticalWeight=number(attrs,"layout_constraintVertical_weight",-1);
            horizontalChainStyle=mode(attrs,"layout_constraintHorizontal_chainStyle",true);verticalChainStyle=mode(attrs,"layout_constraintVertical_chainStyle",true);
            matchConstraintDefaultWidth=mode(attrs,"layout_constraintWidth_default",false);matchConstraintDefaultHeight=mode(attrs,"layout_constraintHeight_default",false);
            matchConstraintMinWidth=dim(attrs,"layout_constraintWidth_min",0);matchConstraintMaxWidth=dim(attrs,"layout_constraintWidth_max",0);
            matchConstraintMinHeight=dim(attrs,"layout_constraintHeight_min",0);matchConstraintMaxHeight=dim(attrs,"layout_constraintHeight_max",0);
            matchConstraintPercentWidth=number(attrs,"layout_constraintWidth_percent",-1);matchConstraintPercentHeight=number(attrs,"layout_constraintHeight_percent",-1);
            constrainedWidth=flag(attrs,"layout_constrainedWidth");constrainedHeight=flag(attrs,"layout_constrainedHeight");
            circleRadius=dim(attrs,"layout_constraintCircleRadius",0);circleAngle=number(attrs,"layout_constraintCircleAngle",0);
            guideBegin=dim(attrs,"layout_constraintGuide_begin",-1);guideEnd=dim(attrs,"layout_constraintGuide_end",-1);guidePercent=number(attrs,"layout_constraintGuide_percent",-1);
            String o=attrs.get("orientation");if(o!=null) { if("vertical".equals(o)||"1".equals(o))orientation=VERTICAL;else if("horizontal".equals(o)||"0".equals(o))orientation=HORIZONTAL;else throw new IllegalArgumentException("orientation: "+o); }
            validate();
        }
        private static String get(AttributeSet a,String name) {
            String value=a.getAttributeValue("http://schemas.android.com/apk/res-auto",name);
            if(value==null)value=a.get(name);if(value==null)value=a.getAttributeValue(null,name);return "@null".equals(value)?null:value;
        }
        private static int dim(AttributeSet a,String name,int fallback) {
            String value=get(a,name);if(value==null)return fallback;if("wrap".equals(value)||"wrap_content".equals(value))return -2;
            float number=Float.parseFloat(value.replaceAll("(dp|dip|sp|px)$",""));
            if(!Float.isFinite(number))throw new IllegalArgumentException(name+": "+value);return Math.round(number);
        }
        private static float number(AttributeSet a,String name,float fallback) { String v=get(a,name);return v==null?fallback:Float.parseFloat(v); }
        private static boolean flag(AttributeSet a,String name) {
            String v=get(a,name);if(v==null||"false".equals(v))return false;if("true".equals(v))return true;throw new IllegalArgumentException(name+": "+v);
        }
        private static int mode(AttributeSet a,String name,boolean chain) {
            String v=get(a,name);if(v==null)return 0;
            String[] values=chain?new String[]{"spread","spread_inside","packed"}:new String[]{"spread","wrap","percent"};
            for(int i=0;i<values.length;i++)if(values[i].equals(v)||Integer.toString(i).equals(v))return i;
            throw new IllegalArgumentException(name+": "+v);
        }
        public void validate() {
            if(width < -2||height < -2)throw new IllegalArgumentException("Invalid layout dimension");
            for(float v:new float[]{horizontalBias,verticalBias})if(!Float.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Bias must be in [0,1]");
            for(float v:new float[]{matchConstraintPercentWidth,matchConstraintPercentHeight,guidePercent})if(!Float.isFinite(v)||v!=-1&&(v<0||v>1))throw new IllegalArgumentException("Percent must be in [0,1]");
            if(!Float.isFinite(horizontalWeight)||!Float.isFinite(verticalWeight)||!Float.isFinite(circleAngle)||circleRadius<0)throw new IllegalArgumentException("Invalid weight/circle");
            if(horizontalChainStyle<0||horizontalChainStyle>2||verticalChainStyle<0||verticalChainStyle>2||matchConstraintDefaultWidth<0||matchConstraintDefaultWidth>2||matchConstraintDefaultHeight<0||matchConstraintDefaultHeight>2)throw new IllegalArgumentException("Invalid constraint mode");
            for(int v:new int[]{matchConstraintMinWidth,matchConstraintMaxWidth,matchConstraintMinHeight,matchConstraintMaxHeight})if(v<0&&v!=-2)throw new IllegalArgumentException("Invalid min/max dimension");
            if(guideBegin < -1||guideEnd < -1||orientation<0||orientation>1)throw new IllegalArgumentException("Invalid guideline");
            parseRatio(dimensionRatio);
        }
    }
}
