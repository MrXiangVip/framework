package com.android.os;

import com.android.util.Log;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public final class MessageQueue implements Queue {
//    Message mMessages;

    Queue<Message>  queue;
    private final boolean mQuitAllowed;
    private String TAG="MessageQueue.";


    MessageQueue(boolean quitAllowed) {
        mQuitAllowed = quitAllowed;
        queue = new LinkedList();

    }
    Message next() {
        Message message=null;
        synchronized(queue) {

            while (queue.size() == 0) {
                try {
                    System.out.println("等待消息 wait here");

                    queue.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if( queue.size()>0 ){
                 message = (Message)poll();
            }
        }
        return  message;
    }
    boolean enqueueMessage(Message msg, long when) {
        Log.d(TAG, "enqueueMessage ");
        msg.when = when;
//        Message p = mMessages;
        synchronized (this) {

            queue.add(msg);
        }
        return true;
    }
    @Override
    public int size() {
        return  queue.size();
    }

    @Override
    public boolean isEmpty() {
        if( queue.size()==0){
            return  true;
        }else{
            return false;
        }
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public Iterator iterator() {
        return null;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public Object[] toArray(Object[] objects) {
        return new Object[0];
    }

    @Override
    public boolean add(Object o) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean addAll(Collection collection) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public boolean retainAll(Collection collection) {
        return false;
    }

    @Override
    public boolean removeAll(Collection collection) {
        return false;
    }

    @Override
    public boolean containsAll(Collection collection) {
        return false;
    }

    @Override
    public boolean offer(Object o) {
        return false;
    }

    @Override
    public Object remove() {
        return null;
    }

    @Override
    public Object poll() {
        Message msg = queue.poll();
        return msg;
    }

    @Override
    public Object element() {
        return null;
    }

    @Override
    public Object peek() {
        return null;
    }
}
