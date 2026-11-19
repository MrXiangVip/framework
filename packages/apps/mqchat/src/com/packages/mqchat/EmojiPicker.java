package com.packages.mqchat;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.view.*;
import com.android.widget.EditText;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Framework adaptation of MQChat Android's docked Unicode emoji picker. */
final class EmojiPicker extends ViewGroup {
    private static final String[][] FACES={
        {"🙂","微笑"},{"😟","担心"},{"😍","色 喜欢"},{"😳","发呆 惊讶"},{"😎","酷 得意"},{"😭","大哭"},{"😌","害羞"},{"🤐","闭嘴"},
        {"😴","睡觉"},{"😫","难过"},{"😰","冷汗"},{"😡","生气"},{"😜","调皮"},{"😁","呲牙"},{"🥲","流泪"},{"🙁","不开心"},
        {"😯","惊讶"},{"😩","抓狂"},{"🥺","委屈"},{"🤭","偷笑"},{"😊","愉快"},{"😮","惊恐"},{"😕","疑惑"},{"😒","白眼"},
        {"😱","惊吓"},{"😄","开心"},{"🫡","敬礼"},{"🤣","笑哭"},{"🤔","思考"},{"🤫","嘘"},{"😵","晕"},{"🥹","感动"},
        {"💀","骷髅"},{"🤯","震惊"},{"🫢","捂嘴"},{"😓","汗"},{"🥱","困"},{"😚","亲亲"},{"🤗","拥抱"},{"🥰","幸福"},
        {"🤓","聪明"},{"😔","失落"},{"😞","失望"},{"😆","大笑"},{"😘","飞吻"},{"🤩","星星眼"},{"😂","笑哭"},{"😅","尴尬"}
    };
    private static final String[][] HEARTS={
        {"❤️","红心 爱心"},{"🧡","橙心"},{"💛","黄心"},{"💚","绿心"},{"💙","蓝心"},{"💜","紫心"},{"🤍","白心"},{"🖤","黑心"},
        {"💔","心碎"},{"💕","两颗心"},{"💞","心动"},{"💗","爱心"},{"💖","闪亮的心"},{"💘","一见钟情"},{"💝","礼物"},{"💐","鲜花"},
        {"🌹","玫瑰"},{"🌻","向日葵"},{"🌷","郁金香"},{"🎉","庆祝"},{"🎂","蛋糕 生日"},{"🎁","礼物"},{"✨","闪光"},{"🌟","星星"}
    };
    private static final String[][] HANDS={
        {"👍","赞"},{"👎","踩"},{"👌","好的"},{"✌️","胜利"},{"🤞","好运"},{"🤟","爱你"},{"🤘","摇滚"},{"🤙","电话"},
        {"👋","再见 你好"},{"🤚","举手"},{"✋","手掌"},{"🖖","问候"},{"👏","鼓掌"},{"🙌","欢呼"},{"👐","双手"},{"🤲","捧手"},
        {"🤝","握手"},{"🙏","谢谢 合十"},{"💪","加油"},{"👊","拳头"},{"✊","加油"},{"🤛","左拳"},{"🤜","右拳"},{"🫶","比心"}
    };

    private final ChatText text;
    private final EditText composer,search;
    private final Grid grid;
    private final List<String> recent=new ArrayList<>();
    private final Path recentFile;
    private boolean open;
    private int category=1;
    EmojiPicker(Context context,ChatText text,EditText composer){
        super(context);this.text=text;this.composer=composer;
        recentFile=Paths.get(System.getProperty("framework.mqchat.dataDir",Paths.get(System.getProperty("user.home"),".mqchat").toString())).resolve("emoji-recent.txt");
        try{if(Files.isRegularFile(recentFile)&&Files.size(recentFile)<16384)for(String value:Files.readAllLines(recentFile,StandardCharsets.UTF_8))if(known(value)&&!recent.contains(value)&&recent.size()<40)recent.add(value);}catch(Exception ignored){}
        search=new EditText(context){@Override protected int measureText(String value){return (int)Math.ceil(text.width(value,mTextSize));}@Override protected void drawTextLine(Canvas canvas,String value,int x,int baseline,int color){text.draw(canvas,value,x,baseline,mTextSize,color);}};search.setId("emojiSearch");search.setHint("搜索表情，如：微笑、爱心、加油");search.setSingleLine(true);search.setMaxCodePoints(80);search.setTextSize(14);search.setOnTextChangedListener((field,value)->{gridReset();invalidate();});addView(search,new LayoutParams(-2,-2));
        grid=new Grid(context);grid.setId("emojiGrid");addView(grid,new LayoutParams(-2,-2));
        String[] labels={"搜索","☺","♡","手势","最近","⌫"};
        for(int i=0;i<labels.length;i++){final int index=i;View tab=new View(context){@Override protected void onDraw(Canvas canvas){canvas.fillRect(0,0,getWidth(),getHeight(),index==category?0xffffffff:0xfff7f7f7);String value=labels[index];text.draw(canvas,value,Math.max(2,(getWidth()-(int)text.width(value,18))/2),29,18,0xff191919);}};tab.setId("emojiTab"+i);tab.setOnClickListener(v->{if(index==5){delete();return;}category=index;gridReset();search.clearFocus();requestLayout();invalidate();});addView(tab,new LayoutParams(-2,-2));}
    }
    private static boolean known(String value){for(String[][] group:new String[][][]{FACES,HEARTS,HANDS})for(String[] entry:group)if(entry[0].equals(value))return true;return false;}
    boolean isOpen(){return open;}
    void toggle(){open=!open;if(!open){search.clearFocus();composer.requestFocus();}}
    void hide(){open=false;search.clearFocus();}
    private void gridReset(){if(grid!=null)grid.offset=0;}
    private void insert(String emoji){composer.replaceSelection(emoji);recent.remove(emoji);recent.add(0,emoji);while(recent.size()>40)recent.remove(recent.size()-1);try{Files.createDirectories(recentFile.getParent());Files.write(recentFile,recent,StandardCharsets.UTF_8);}catch(Exception ignored){}invalidate();}
    private void delete(){int left=Math.min(composer.getSelectionStart(),composer.getSelectionEnd()),right=Math.max(composer.getSelectionStart(),composer.getSelectionEnd());if(left==right&&left>0){java.text.BreakIterator iterator=java.text.BreakIterator.getCharacterInstance();iterator.setText(composer.getText());left=iterator.preceding(left);}if(left>=0&&left<right){composer.setSelection(left,right);composer.replaceSelection("");}}
    private List<String[]> entries(){List<String[]> entries=new ArrayList<>();if(category==4){for(String value:recent)entries.add(new String[]{value,"最近使用"});}else if(category==0){String query=search.getText().trim().toLowerCase(Locale.ROOT);for(String[][] group:new String[][][]{FACES,HEARTS,HANDS})for(String[] entry:group)if((entry[0]+entry[1]).toLowerCase(Locale.ROOT).contains(query))entries.add(entry);}else entries.addAll(Arrays.asList(category==2?HEARTS:category==3?HANDS:FACES));return entries;}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(400,w),resolveSize(280,h));for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(44,MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int w=getWidth();for(int i=0;i<6;i++)findViewById("emojiTab"+i).layout(i*w/6,0,(i+1)*w/6,44);int top=category==0?94:54;if(category==0)search.layout(12,50,Math.max(12,w-12),88);else search.layout(0,0,0,0);grid.layout(8,top,Math.max(8,w-8),Math.max(top,getHeight()-8));}
    @Override protected void onDraw(Canvas canvas){canvas.drawColor(0xffededed);}
    private final class Grid extends View {
        int offset,lastY;boolean dragging;
        Grid(Context context){super(context);}
        private void scroll(int delta){int total=((entries().size()+7)/8)*46;offset=Math.max(0,Math.min(Math.max(0,total-getHeight()),offset+delta));invalidate();}
        @Override protected void onDraw(Canvas canvas){List<String[]> values=entries();scrollClamp(values.size());canvas.save();try{canvas.clipRect(0,0,getWidth(),getHeight());if(values.isEmpty())text.draw(canvas,category==4?"使用过的表情会出现在这里":"没有找到相关表情",12,42,13,0xff999999);for(int i=0;i<values.size();i++){int y=i/8*46-offset;if(y+46<0||y>getHeight())continue;String emoji=values.get(i)[0];int cell=getWidth()/8;text.draw(canvas,emoji,i%8*getWidth()/8+Math.max(0,(cell-(int)text.width(emoji,27))/2),y+34,27,0xff191919);}}finally{canvas.restore();}}
        private void scrollClamp(int count){offset=Math.min(offset,Math.max(0,((count+7)/8)*46-getHeight()));}
        @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_SCROLL){scroll(event.getScrollDelta()*46);return true;}if(event.getAction()==MotionEvent.ACTION_DOWN){lastY=event.getY();dragging=false;}else if(event.getAction()==MotionEvent.ACTION_MOVE){int delta=lastY-event.getY();if(Math.abs(delta)>3)dragging=true;scroll(delta);lastY=event.getY();}else if(event.getAction()==MotionEvent.ACTION_UP&&!dragging&&event.getX()>=0&&event.getX()<getWidth()&&event.getY()>=0&&event.getY()<getHeight()){int index=(event.getY()+offset)/46*8+event.getX()*8/Math.max(1,getWidth());List<String[]> values=entries();if(index<values.size())insert(values.get(index)[0]);}return true;}
    }
}
