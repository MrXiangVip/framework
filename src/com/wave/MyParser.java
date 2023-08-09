package com.wave;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;


public class MyParser {

    public ArrayList<String> list = new ArrayList<>();

    public void pull() throws Exception {

        //获得工厂
        XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
        //获得xml的解析器
        XmlPullParser parser = parserFactory.newPullParser();
        //给解析器设置一个输入源
//        parser.setInput(new FileInputStream(new File("/home/xshx/IdeaProjects/untitled/res/main.xml")), "UTF-8");
        parser.setInput(new FileInputStream(new File("/home/xshx/IdeaProjects/untitled/res/layout/activity_main.xml")), "UTF-8");
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT:
                    System.out.println("start doc");
                    break;
                case XmlPullParser.START_TAG:
                    //开始标签,parser获取当前事件对应的元素名字
                    String tagName = parser.getName();
                    System.out.println("start tag: < "+tagName);
                    int count = parser.getAttributeCount();
                    System.out.println(count);
                    for(int i =0; i<count; i++){
                        String attrName = parser.getAttributeName(i);
                        String attrValue = parser.getAttributeValue(i);
                        System.out.println( attrName+"    "+attrValue);
                    }

                    list.add( tagName );
                    break;
                case XmlPullParser.END_TAG:
                    String name = parser.getName();
                    System.out.println("end tag: "+name+" > ");

                    break;
                default:
                    break;

            }
            //调用parser.next()方法解析下一个元素
            eventType = parser.next();
        }
//        for (StaInfo staInfos1 : staInfos) {
//            System.out.println("pullparser=======" +staInfos1);
//        }

    }
}

