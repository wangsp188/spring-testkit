package com.testkit.server;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * @Description 本机ip
 * @Author shaopeng
 * @Date 2022/6/14
 * @Version 1.0
 */
public class LocalIpUtil {

    /**
     * 本机ip
     */
    private static String localIp = null;

    /**
     * 本地ip尾部数组
     */
    private static String localIpTail = null;


    static {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            if (allNetInterfaces != null) {
                while (allNetInterfaces.hasMoreElements()) {
                    NetworkInterface netInterface = allNetInterfaces.nextElement();
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress inetAddress = addresses.nextElement();
                        if (inetAddress instanceof Inet4Address) {
                            if (inetAddress.getHostAddress()!=null && !inetAddress.getHostAddress().isEmpty() && !inetAddress.getHostAddress().startsWith("127")) {
                                localIp = inetAddress.getHostAddress();
                                if (localIp.contains(".")) {
                                    localIpTail = localIp.substring(localIp.lastIndexOf(".") + 1);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    /**
     * 非空
     *
     * @return 获取本机ip
     */
    public static String getLocalIp() {
        return localIp;
    }

    /**
     * 非空
     *
     * @return 获取本机ip尾部
     */
    public static String getLocalIpTail() {
        return localIpTail;
    }

}
