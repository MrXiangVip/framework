package com.android;

public class HelloServiceImpl implements  HelloService {
    @Override
    public void sayHello(String name) {
        System.out.println(" hello "+name);
    }

    @Override
    public String getName() {
        return "nancy";
    }

}
