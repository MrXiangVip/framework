package com.android;

public interface Request {

    String getInterfaceName();
    String getMethodName();
    Object[]  getArguments();
    Class<?>[]   getParameterTypes();
}
