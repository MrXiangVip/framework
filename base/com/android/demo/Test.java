package com.android.demo;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class Test implements ITest{
    public static final int SYSTEM_UI_FLAG_FULLSCREEN =        0x0000044;
    public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 0x0000004;


    public static void main(String[] argv) throws Exception {



        System.out.println("hello");
        System.out.println(System.getProperty("user.dir"));
        /** * 方法一：获取当前可执行jar包所在目录 */
        String filePath = System.getProperty("java.class.path");
        String pathSplit = System.getProperty("path.separator");//得到当前操作系统的分隔符，windows下是";",linux下是":"

/** * 若没有其他依赖，则filePath的结果应当是该可运行jar包的绝对路径， * 此时我们只需要经过字符串解析，便可得到jar所在目录 */
        if(filePath.contains(pathSplit)){
            filePath = filePath.substring(0,filePath.indexOf(pathSplit));
        }else if (filePath.endsWith(".jar")) {

            //截取路径中的jar包名,可执行jar包运行的结果里包含".jar"
            filePath = filePath.substring(0, filePath.lastIndexOf(File.separator) + 1);
        }
        System.out.println("jar包所在目录："+filePath);



        int value = SYSTEM_UI_FLAG_FULLSCREEN |SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if( value==0 ){
            System.out.println("diff"+value);

        }else{
            System.out.println( "xiantong"+value);

        }
        System.out.println( Integer.toHexString(value));


        int dstValue=0x400;
        System.out.println( Integer.toHexString( dstValue & value));



        boolean isTranslucent =false;

        System.out.println(isTranslucentOrFloating() );

        System.out.println( Thread.currentThread().getId() +Thread.currentThread().getName() );

        System.out.println( getProcessID() );


        MyParser parser = new MyParser();
        parser.pull();
        System.out.println( parser.list );


        Test test = new Test();
        System.out.println( test.getKey());
//        new Throwable().printStackTrace();
    }

    public String getKey( ){
        return  getClass().getSimpleName();
    }
    public  static boolean isTranslucentOrFloating(){
        boolean isTranslucent =false;
        if( true ){
            isTranslucent =false;
        }else{
            isTranslucent = true;
        }
        return  isTranslucent;
    }

    public static final int getProcessID() {

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        System.out.println(runtimeMXBean.getName());

        return Integer.valueOf(runtimeMXBean.getName().split("@")[0])

                .intValue();

    }
}
