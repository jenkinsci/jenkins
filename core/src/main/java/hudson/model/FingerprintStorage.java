package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.io.IOException;

public abstract class FingerprintStorage implements ExtensionPoint {

    static FingerprintStorage get(){
        return ExtensionList.lookup(FingerprintStorage.class).get(0);
    }

    public abstract void save(Fingerprint fp) throws IOException;

    public abstract Fingerprint load(byte[] md5sum) throws IOException;

}
