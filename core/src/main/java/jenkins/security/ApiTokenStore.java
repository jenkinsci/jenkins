/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.util.Secret;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.mindrot.jbcrypt.BCrypt;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ApiTokenStore {
    private static final Logger LOGGER = Logger.getLogger(OldDataMonitor.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    
    private static final Comparator<HashedToken> SORT_BY_LOWERCASED_NAME =
            Comparator.comparing(hashedToken -> hashedToken.getName().toLowerCase());
    
    /**
     * Determine the (log of) number of rounds we need to apply when hashing the token
     * default value corresponds to 
     * BCrypt#GENSALT_DEFAULT_LOG2_ROUNDS is 10 which is way too small in 2018
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static int BCRYPT_LOG_ROUND =
            SystemProperties.getInteger(ApiTokenStore.class.getName() + ".bcryptLogRound", 13);
    /**
     * Determine the number of attempt to generate an unique prefix (over 4096 possibilities) that is not currently used
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static final int MAX_ATTEMPTS =
            SystemProperties.getInteger(ApiTokenStore.class.getName() + ".maxAttempt", 100);
    
    private static final int TOKEN_LENGTH_V2 = 36;
    /** single hex-character */
    private static final String LEGACY_VERSION = "1";
    private static final String HASH_VERSION = "2";
    
    private static final String LEGACY_PREFIX = "";

    /**
     * Cache the computation of hash in order to speed up API call that were already verified some times ago
     * Does not keep deleted data alive, just optimize the computation of the hashes.
     */
    private static final HashCache HASH_CACHE = new HashCache();
    
    private List<HashedToken> tokenList;
    private transient Map<String, Node<HashedToken>> prefixToTokenList;
    
    public ApiTokenStore() {
        this.init();
    }
    
    public ApiTokenStore readResolve() {
        this.init();
        return this;
    }
    
    private void init() {
        if (this.tokenList == null) {
            this.tokenList = new ArrayList<>();
        }
        this.prefixToTokenList = new HashMap<>();
    }
    
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    public synchronized @Nonnull List<HashedToken> getTokenListSortedByName() {
        List<ApiTokenStore.HashedToken> sortedTokenList = tokenList.stream()
                .sorted(SORT_BY_LOWERCASED_NAME)
                .collect(Collectors.toList());
        
        return sortedTokenList;
    }
    
    /**
     * After a load from the disk, we need to re-populate the prefix map
     */
    public synchronized void optimize() {
        this.prefixToTokenList.clear();
        tokenList.forEach(this::addTokenInPrefixMap);
    }
    
    private void addToken(HashedToken token) {
        this.tokenList.add(token);
        this.addTokenInPrefixMap(token);
    }
    
    private void addTokenInPrefixMap(HashedToken token) {
        String prefix = token.value.prefix;
        Node<HashedToken> newNode = new Node<>(token);
        if (prefixToTokenList.containsKey(prefix)) {
            Node<HashedToken> existingNode = prefixToTokenList.get(prefix);
            existingNode.addNode(newNode);
        } else {
            prefixToTokenList.put(prefix, newNode);
        }
    }
    
    /**
     * Defensive approach to avoid involuntary change since the UUIDs are generated at startup only for UI
     * and so between restart they change
     */
    public synchronized void reconfigure(@Nonnull Map<String, JSONObject> tokenStoreDataMap) {
        tokenList.forEach(hashedToken -> {
            JSONObject receivedTokenData = tokenStoreDataMap.get(hashedToken.uuid);
            if (receivedTokenData == null) {
                LOGGER.log(Level.INFO, "No token received for {}", hashedToken.uuid);
                return;
            }
            
            String name = receivedTokenData.getString("tokenName");
            if (StringUtils.isBlank(name)) {
                LOGGER.log(Level.INFO, "Empty name received for {}, we do not care about it", hashedToken.uuid);
                return;
            }
            
            hashedToken.setName(name);
        });
    }
    
    private static class Node<T> {
        private T value;
        private Node<T> next;
        
        Node(T value) {
            this.value = value;
        }
        
        public void addNode(@Nonnull Node<T> other) {
            if (next == null) {
                this.next = other;
            } else {
                this.next.addNode(other);
            }
        }
    }
    
    public synchronized void generateTokenFromLegacy(@Nonnull Secret newLegacyApiToken){
        deleteAllLegacyTokens();
        addLegacyToken(newLegacyApiToken);
    }
    
    private void deleteAllLegacyTokens(){
        // normally there is only one, but just in case
        for (int i = tokenList.size() - 1; i >= 0; i--) {
            HashedToken token = tokenList.get(i);
            if (token.isLegacy()) {
                tokenList.remove(i);
            
                removeTokenFromPrefixMap(token);
            }
        }
    }
    
    private void addLegacyToken(@Nonnull Secret legacyToken){
        String tokenUserUseNormally = Util.getDigestOf(legacyToken.getPlainText());
    
        String secretValueHashed = this.hashSecret(tokenUserUseNormally);
    
        HashValue hashValue = new HashValue(LEGACY_VERSION, LEGACY_PREFIX, secretValueHashed);
        HashedToken token = HashedToken.buildNew(Messages.ApiTokenProperty_LegacyTokenName(), hashValue);
    
        this.addToken(token);
    }
    
    public synchronized @Nonnull String generateNewTokenAndReturnHiddenValue(@Nonnull String name) {
        // 16x8=128bit worth of randomness, since we use md5 digest as the API token
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String secretValue = Util.toHexString(random);
        String prefix = generatePrefix();
        String tokenTheUserWillUse = HASH_VERSION + prefix + secretValue;
        assert tokenTheUserWillUse.length() == 1 + 3 + 32;
        
        String secretValueHashed = this.hashSecret(secretValue);
        
        HashValue hashValue = new HashValue(HASH_VERSION, prefix, secretValueHashed);
        HashedToken token = HashedToken.buildNew(name, hashValue);
        
        this.addToken(token);
        
        return tokenTheUserWillUse;
    }
    
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    private @Nonnull String hashSecret(@Nonnull String secretValue) {
        String salt = BCrypt.gensalt(BCRYPT_LOG_ROUND, RANDOM);
        return BCrypt.hashpw(secretValue, salt);
    }
    
    private @Nonnull String generatePrefix() {
        int i = 0;
        boolean unique;
        String currentPrefix;
        
        do {
            currentPrefix = generateRandomPrefix();
            unique = !prefixToTokenList.containsKey(currentPrefix);
            i++;
        } while (i < MAX_ATTEMPTS && !unique);
        
        Level logLevel = Level.FINE;
        if (i == MAX_ATTEMPTS) {
            logLevel = Level.WARNING;
        }
        LOGGER.log(logLevel, "Prefix generated after {0}/{1} attempts", new Object[]{i, MAX_ATTEMPTS});
        
        return currentPrefix;
    }
    
    /**
     * Generate random 3-hex-character
     */
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    private @Nonnull String generateRandomPrefix() {
        int prefixInteger = RANDOM.nextInt(4096);

        String prefixString = Integer.toHexString(prefixInteger);
        return StringUtils.leftPad(prefixString, 3, '0');
    }
    
    public synchronized boolean doesContainToken(@Nonnull String token) {
        String prefixToSearch;
        String plainToken;
        
        if (isLegacyToken(token)) {
            prefixToSearch = LEGACY_PREFIX;
            plainToken = token;
        } else {
            prefixToSearch = getPrefixOfToken(token);
            plainToken = getHashOfToken(token);
        }
        
        return searchMatchUsingPrefix(prefixToSearch, plainToken);
    }
    
    private boolean isLegacyToken(String token) {
        return token.length() != TOKEN_LENGTH_V2;
    }
    
    /**
     * [1: version][3: prefix][32: real token]
     * ^^^^^^^^^^^^---------------------------
     */
    private String getVersionOfToken(String token) {
        return String.valueOf(token.charAt(0));
    }
    
    /**
     * [1: version][3: prefix][32: real token]
     * ------------^^^^^^^^^^^----------------
     */
    private String getPrefixOfToken(String token) {
        return token.substring(1, 4);
    }
    
    /**
     * [1: version][3: prefix][32: real token]
     * -----------------------^^^^^^^^^^^^^^^^
     */
    private String getHashOfToken(String token) {
        return token.substring(4);
    }
    
    private boolean searchMatchUsingPrefix(String prefix, String plainToken) {
        String plainTokenCacheKey = HASH_CACHE.getCorrespondingCacheKey(plainToken);
        String uuidFromCache = HASH_CACHE.getCachedUuid(plainTokenCacheKey);
        if(uuidFromCache != null){
            for (HashedToken token : tokenList) {
                if (token.uuid.equals(uuidFromCache)) {
                    LOGGER.log(Level.FINER, "Cache hit for prefix = ", prefix);
                    token.incrementUse();
                    HASH_CACHE.insertOrRefreshCache(plainTokenCacheKey, token.uuid);
                    return true;
                }
            }
        }
        LOGGER.log(Level.FINER, "Cache miss for prefix = ", prefix);
        
        Node<HashedToken> node = this.prefixToTokenList.get(prefix);
        while (node != null) {
            boolean matchFound = node.value.match(plainToken);
            if (matchFound) {
                node.value.incrementUse();
    
                HASH_CACHE.insertOrRefreshCache(plainTokenCacheKey, node.value.uuid);
                return true;
            } else {
                node = node.next;
            }
        }
        
        return false;
    }
    
    public synchronized @CheckForNull HashedToken revokeToken(@Nonnull String tokenId) {
        for (int i = 0; i < tokenList.size(); i++) {
            HashedToken token = tokenList.get(i);
            if (token.uuid.equals(tokenId)) {
                tokenList.remove(i);
                
                removeTokenFromPrefixMap(token);
                return token;
            }
        }
        
        return null;
    }
    
    private void removeTokenFromPrefixMap(HashedToken token) {
        String prefix = token.value.prefix;
        Node<HashedToken> node = prefixToTokenList.get(prefix);
        if (node == null) {
            // normally not the case
            return;
        }
        
        // first node, we replace it by the next one or nothing
        if (node.value.uuid.equals(token.uuid)) {
            if (node.next == null) {
                prefixToTokenList.remove(prefix);
            } else {
                prefixToTokenList.put(prefix, node.next);
            }
        } else {
            Node<HashedToken> previousNode = node;
            node = node.next;
            while (node != null) {
                // 2-nth node, we replace the previous.next with new value
                // but do not touch the initial node
                if (node.value.uuid.equals(token.uuid)) {
                    if (node.next == null) {
                        previousNode.next = null;
                    } else {
                        previousNode.next = node.next;
                    }
                    return;
                }
                
                previousNode = node;
                node = node.next;
            }
        }
    }
    
    public synchronized void renameToken(@Nonnull String tokenId, @Nonnull String newName) {
        for (HashedToken token : tokenList) {
            if (token.uuid.equals(tokenId)) {
                token.rename(newName);
                return;
            }
        }
    }
    
    /**
     * [1: version][3: prefix][32: real token]
     */
    @Immutable
    private static class HashValue {
        /**
         * Serve as an optimizer to avoid hashing all the tokens for the token-check
         * not a "confidential" information
         */
        private final String prefix;
        /** To ease future implementation */
        private final String version;
        /** The only confidential information. The token is stored only as a BCrypt hash */
        private final String hash;
        
        public HashValue(String version, String prefix, String hash) {
            this.version = version;
            this.prefix = prefix;
            this.hash = hash;
        }
    }
    
    public static class HashedToken {
        // to ease the modification of the token through the UI
        private transient String uuid;
        private String name;
        private Date creationDate;
        
        private HashValue value;
        
        private Date lastUseDate;
        private Integer useCounter;
        
        public HashedToken() {
            this.init();
        }
        
        public HashedToken readResolve() {
            this.init();
            return this;
        }
        
        private void init() {
            this.uuid = UUID.randomUUID().toString();
        }
        
        public static @Nonnull HashedToken buildNew(@Nonnull String name, @Nonnull HashValue value) {
            HashedToken result = new HashedToken();
            result.name = name;
            result.creationDate = new Date();
            
            result.value = value;
            
            return result;
        }
        
        public void rename(String newName) {
            this.name = newName;
        }
    
        /**
         * This operation should take some time (between 100ms and 1s)
         */
        public boolean match(String plainToken) {
            return BCrypt.checkpw(plainToken, value.hash);
        }
        
        public String getName() {
            return name;
        }
        
        public int getUseCounter() {
            return useCounter == null ? 0 : useCounter;
        }
        
        public long getNumDaysUse() {
            return lastUseDate == null ? 0 : computeDeltaDays(lastUseDate.toInstant(), Instant.now());
        }
        
        public long getNumDaysCreation() {
            // should not happen but just in case
            return creationDate == null ? 0 : computeDeltaDays(creationDate.toInstant(), Instant.now());
        }
        
        public String getUuid() {
            return this.uuid;
        }
        
        private long computeDeltaDays(Instant a, Instant b) {
            long deltaDays = ChronoUnit.DAYS.between(a, b);
            deltaDays = Math.max(0, deltaDays);
            return deltaDays;
        }
        
        public boolean isLegacy() {
            return this.value.version.equals(LEGACY_VERSION);
        }
        
        public void incrementUse() {
            this.useCounter = useCounter == null ? 1 : useCounter + 1;
            this.lastUseDate = new Date();
        }
        
        public void setName(String name) {
            this.name = name;
        }
    }
    
    public static class HashCache {
        /**
         * A "session" duration, meaning we do not compute the long hash each time but use a weak hash instead after the first success
         */
        @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
        public static long HASH_CACHE_TIMEOUT_IN_MS =
                SystemProperties.getLong(ApiTokenStore.class.getName() + ".hashCacheTimeout", (long)(5 * 60 * 1000));
        
        /**
         * At minimum we compute the long hash every 30 minutes
         */
        @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
        public static long HASH_CACHE_MAX_LIVETIME_IN_MS =
                SystemProperties.getLong(ApiTokenStore.class.getName() + ".hashCacheMaxLivetime", (long)(30 * 60 * 1000));
    
        @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
        public static boolean HASH_CACHE_DISABLED =
                SystemProperties.getBoolean(ApiTokenStore.class.getName() + ".hashCacheDisabled", false);
    
        @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
        public static int HASH_CACHE_MIN_SIZE_FOR_CLEANUP =
                SystemProperties.getInteger(ApiTokenStore.class.getName() + ".hashCacheMinSizeForCleanup", 20);
    
        private static final byte[] HASH_CACHE_SALT;
        static {
            // no requirement to keep it between restart since the cache is temporary
            SecureRandom random = new SecureRandom();
            HASH_CACHE_SALT = new byte[16];
            random.nextBytes(HASH_CACHE_SALT);
        }
    
        private static final String HASH_ALGORITHM = "SHA-256";
        
        private final Map<String, CacheEntry> cache = new HashMap<>();
    
        @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
        public @Nonnull String getCorrespondingCacheKey(String plainToken){
            if(HASH_CACHE_DISABLED){
                return "cache-disabled";
            }
            byte[] hashedTokenBytes = hashWithSalt(plainToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedTokenBytes);
        }
    
        @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
        private @Nonnull byte[] hashWithSalt(byte[] tokenBytes){
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance(HASH_ALGORITHM);
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError("There is no " + HASH_ALGORITHM + " available in this system");
            }
            digest.update(HASH_CACHE_SALT);
            return digest.digest(tokenBytes);
        }
    
        public synchronized @CheckForNull String getCachedUuid(String cacheKey){
            if(HASH_CACHE_DISABLED){
                return null;
            }
            
            cleanup();
            
            if(cache.containsKey(cacheKey)){
                CacheEntry entry = cache.get(cacheKey);
                if(entry != null){
                    if(entry.hasExpired()){
                        cache.remove(cacheKey);
                    }else{
                        return entry.data;
                    }
                }
            }

            return null;
        }
    
        private void cleanup(){
            if(cache.size() < HASH_CACHE_MIN_SIZE_FOR_CLEANUP){
                return;
            }
    
            cache.entrySet().removeIf(entry -> entry.getValue().hasExpired());
        }
        
        public synchronized void insertOrRefreshCache(String cacheKey, String uuid){
            if(HASH_CACHE_DISABLED){
                return;
            }

            CacheEntry entry = cache.get(cacheKey);
            if(entry == null){
                cache.put(cacheKey, new CacheEntry(uuid));
            }else{
                entry.touch();
            }
        }
        
        private static class CacheEntry {
            LocalDateTime firstCheck;
            LocalDateTime lastCheck;
            String data;
    
            CacheEntry(String data){
                this.data = data;
                this.firstCheck = this.lastCheck = LocalDateTime.now();
            }
            
            public void touch(){
                this.lastCheck = LocalDateTime.now();
            }
            
            public boolean hasExpired(){
                LocalDateTime now = LocalDateTime.now();
                if(now.isAfter(lastCheck.plus(HASH_CACHE_TIMEOUT_IN_MS, ChronoUnit.MILLIS))){
                    // not recently used
                    return true;
                }
                if(now.isAfter(firstCheck.plus(HASH_CACHE_MAX_LIVETIME_IN_MS, ChronoUnit.MILLIS))){
                    // full check required after a certain time
                    return true;
                }
                return false;
            }
        }
    }
}
