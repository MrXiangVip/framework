package com.wave;

public interface Request {

    String getInterfaceName();
    String getMethodName();
    Object[]  getArguments();
    Class<?>[]   getParameterTypes();
}
