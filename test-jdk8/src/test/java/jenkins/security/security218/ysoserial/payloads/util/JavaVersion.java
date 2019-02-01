package jenkins.security.security218.ysoserial.payloads.util;


/**
 * @author mbechler
 *
 */
public class JavaVersion {

    
    public int major;
    public int minor;
    public int update;
    
  
    
    public static JavaVersion getLocalVersion() {
        String property = System.getProperties().getProperty("java.version");
        if ( property == null ) {
            return null;
        }
        JavaVersion v = new JavaVersion();
        String parts[] = property.split("\\.|_|-");
        v.major   = Integer.parseInt(parts[1]);
        v.minor   = Integer.parseInt(parts[2]);
        v.update  = Integer.parseInt(parts[3]);
        return v;
    }
    
    
    public static boolean isAnnInvHUniversalMethodImpl() {
        JavaVersion v = JavaVersion.getLocalVersion();
        return v != null && (v.major < 8 || (v.major == 8 && v.update <= 71));
    }
}

