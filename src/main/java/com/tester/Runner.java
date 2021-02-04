package com.tester;

public class Runner {
    public static void main(String[] args) {
        QPSContext qpsContext = new QPSContext();
        qpsContext.setExpectQps(30000);
        qpsContext.setUrl("http://localhost:8080/index");
        QPSUtil qpsUtil = new QPSUtil();
        qpsUtil.QPSTest(qpsContext);
    }
}
