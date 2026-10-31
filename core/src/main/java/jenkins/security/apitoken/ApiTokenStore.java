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

package jenkins.security.apitoken;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.util.Secret;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jenkins.security.Messages;
import net.jcip.annotations.Immutable;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class ApiTokenStore {
    private static final Logger LOGGER = Logger.getLogger(ApiTokenStore.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Comparator<HashedToken> SORT_BY_LOWERCASED_NAME =
            Comparator.comparing(hashedToken -> hashedToken.getName().toLowerCase(Locale.ENGLISH));

    private static final int TOKEN_LENGTH_V2 = 34;
    /** two hex characters, avoid starting with 0 to avoid troubles */
    private static final String LEGACY_VERSION = "10";
    private static final String HASH_VERSION = "11";

    private static final String HASH_ALGORITHM = "SHA-256";
    /** the size in bytes of a hash made with {@link #HASH_ALGORITHM} */
    private static final int SHA_256_LENGTH = 32;

    private List<HashedToken> tokenList;

    public ApiTokenStore() {
        this.init();
    }

    private Object readResolve() {
        this.init();
        return this;
    }

    private void init() {
        if (this.tokenList == null) {
            this.tokenList = new ArrayList<>();
        }
    }

    public synchronized @NonNull Collection<HashedToken> getTokenListSortedByName() {
        return tokenList.stream()
                .sorted(SORT_BY_LOWERCASED_NAME)
                .collect(Collectors.toList());
    }

    /**
     * A copy of the list of entries, in the order in which they were added. Unlike {@link #getTokenListSortedByName()} it does
     * not sort them, which is what a caller that only scans the entries needs: it does not pay for the sort while it holds the lock.
     * <p>
     * It is a snapshot: it does not change when the store does, and changing it does not change the store. The entries
     * themselves are not copied.
     */
    @Restricted(NoExternalUse.class)
    public synchronized @NonNull List<HashedToken> getTokenListSnapshot() {
        return new ArrayList<>(tokenList);
    }

    private void addToken(HashedToken token) {
        this.tokenList.add(token);
    }

    /**
     * Defensive approach to avoid involuntary change since the UUIDs are generated at startup only for UI
     * and so between restart they change
     */
    public synchronized void reconfigure(@NonNull Map<String, JSONObject> tokenStoreDataMap) {
        tokenList.forEach(hashedToken -> {
            JSONObject receivedTokenData = tokenStoreDataMap.get(hashedToken.uuid);
            if (receivedTokenData == null) {
                LOGGER.log(Level.INFO, "No token received for {0}", hashedToken.uuid);
                return;
            }

            String name = receivedTokenData.getString("tokenName");
            if (name == null || name.isBlank()) {
                LOGGER.log(Level.INFO, "Empty name received for {0}, we do not care about it", hashedToken.uuid);
                return;
            }

            hashedToken.setName(name);
        });
    }

    /**
     * Remove the legacy token present and generate a new one using the given secret.
     */
    public synchronized void regenerateTokenFromLegacy(@NonNull Secret newLegacyApiToken) {
        deleteAllLegacyAndGenerateNewOne(newLegacyApiToken, false);
    }

    /**
     * Same as {@link #regenerateTokenFromLegacy(Secret)} but only applied if there is an existing legacy token.
     * <p>
     * Otherwise, no effect.
     */
    public synchronized void regenerateTokenFromLegacyIfRequired(@NonNull Secret newLegacyApiToken) {
        if (tokenList.stream().noneMatch(HashedToken::isLegacy)) {
            deleteAllLegacyAndGenerateNewOne(newLegacyApiToken, true);
        }
    }

    private void deleteAllLegacyAndGenerateNewOne(@NonNull Secret newLegacyApiToken, boolean migrationFromExistingLegacy) {
        deleteAllLegacyTokens();
        addLegacyToken(newLegacyApiToken, migrationFromExistingLegacy);
    }

    private void deleteAllLegacyTokens() {
        // normally there is only one, but just in case
        tokenList.removeIf(HashedToken::isLegacy);
    }

    private void addLegacyToken(@NonNull Secret legacyToken, boolean migrationFromExistingLegacy) {
        String tokenUserUseNormally = Util.getDigestOf(legacyToken.getPlainText());

        String secretValueHashed = this.plainSecretToHashInHex(tokenUserUseNormally);

        HashValue hashValue = new HashValue(LEGACY_VERSION, secretValueHashed);
        HashedToken token = HashedToken.buildNewFromLegacy(hashValue, migrationFromExistingLegacy);

        this.addToken(token);
    }

    /**
     * @return {@code null} iff there is no legacy token in the store, otherwise the legacy token is returned
     */
    public synchronized @Nullable HashedToken getLegacyToken() {
        return tokenList.stream()
                .filter(HashedToken::isLegacy)
                .findFirst()
                .orElse(null);
    }

    /**
     * Create a new token with the given name and return its id and secret value.
     * Result meant to be sent / displayed and then discarded.
     */
    public synchronized @NonNull TokenUuidAndPlainValue generateNewToken(@NonNull String name) {
        return generateNewToken(name, null);
    }

    /**
     * Create a new token with the given name and expiration and return its id and secret value.
     * Result meant to be sent / displayed and then discarded.
     * <p>
     * The secret is random, 128 bits of it, so it is never checked against the secrets that the store already holds, unlike
     * the one of {@link #addFixedNewTokenRejectingKnownSecret}.
     */
    public synchronized @NonNull TokenUuidAndPlainValue generateNewToken(@NonNull String name, @Nullable LocalDate expirationDate) {
        // 16x8=128bit worth of randomness, using brute-force you need on average 2^127 tries (~10^37)
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        // 32-char in hex
        String secretValue = Util.toHexString(random);
        String tokenTheUserWillUse = HASH_VERSION + secretValue;
        assert tokenTheUserWillUse.length() == 2 + 32;

        HashedToken token = prepareAndStoreToken(name, secretValue, expirationDate);

        return new TokenUuidAndPlainValue(token.uuid, tokenTheUserWillUse, expirationDate, token.isAboutToExpire());
    }

    private static final int VERSION_LENGTH = 2;
    private static final int HEX_CHAR_LENGTH = 32;
    private static final Pattern CHECK_32_HEX_CHAR = Pattern.compile("[a-f0-9]{32}");

    /**
     * Be careful with this method. Depending on how the tokenPlainValue was stored/sent to this method,
     * it could be a good idea to generate a new token randomly and revoke this one.
     * <p>
     * The secret is stored even if the store holds it already, which then has two entries: each one has its own
     * expiration. See {@link #addFixedNewTokenRejectingKnownSecret} for the variant that refuses that.
     */
    public synchronized @NonNull String addFixedNewToken(@NonNull String name, @NonNull String tokenPlainValue, @Nullable LocalDate expirationDate) {
        return addFixedNewToken(name, tokenPlainValue, expirationDate, false);
    }

    /**
     * Same as {@link #addFixedNewToken(String, String, LocalDate)}, but refuses a secret that the store holds already.
     * <p>
     * Registering the same secret again creates a new entry with a new expiration, which keeps the secret valid for as
     * long as the caller repeats it. A maximum lifetime of API tokens bounds the entries, not the secrets, unless this
     * is refused: the web endpoint that registers a fixed token calls this method, instead of the other one, while the
     * maximum is enforced for the owner of the token. See {@link ApiTokenLifetimePolicy}.
     * <p>
     * What is refused is a secret that <em>any</em> entry of this store has: it does not matter whether that entry has
     * expired, nor which version it has, so the secret of a legacy token, which is held by the entry of that token,
     * is known as well. The letter case of the submitted hexadecimal digits does not matter either. The check and the
     * insertion are one atomic operation: of several calls with the same secret at the same time, one succeeds.
     * <p>
     * Only the store of this user is searched. The same secret can be registered for another user, which gives an
     * independent credential, because authentication finds the user by its name first. Revoking every entry that holds the
     * secret makes the store forget it: the same secret can be registered again after that.
     * <p>
     * The rejection tells the caller that this user holds the secret, even through an entry that has expired. That is not
     * exploitable by someone who does not know the secret already, the caller sent it.
     *
     * @throws ApiTokenPolicyViolationException with the reason {@link ApiTokenPolicyViolationException.Reason#SECRET_ALREADY_REGISTERED}
     *         when the store holds the secret. Nothing is stored then.
     * @throws IllegalArgumentException when the token value is malformed, which is checked first, as
     *         {@link #addFixedNewToken(String, String, LocalDate)} does
     */
    @Restricted(NoExternalUse.class)
    public synchronized @NonNull String addFixedNewTokenRejectingKnownSecret(@NonNull String name, @NonNull String tokenPlainValue, @Nullable LocalDate expirationDate) {
        return addFixedNewToken(name, tokenPlainValue, expirationDate, true);
    }

    /**
     * The implementation of {@link #addFixedNewToken(String, String, LocalDate)} and
     * {@link #addFixedNewTokenRejectingKnownSecret}. It is synchronized, so that the search for the secret and the insertion
     * of the entry are one operation.
     */
    @SuppressFBWarnings(value = "UNSAFE_HASH_EQUALS", justification = "Comparison only validates version of the specified token")
    private synchronized @NonNull String addFixedNewToken(@NonNull String name, @NonNull String tokenPlainValue, @Nullable LocalDate expirationDate, boolean rejectKnownSecret) {
        if (tokenPlainValue.length() != VERSION_LENGTH + HEX_CHAR_LENGTH) {
            LOGGER.log(Level.INFO, "addFixedNewToken, length received: {0}" + tokenPlainValue.length());
            throw new IllegalArgumentException("The token must consist of 2 characters for the version and 32 hex-characters for the secret");
        }

        String hashVersion = tokenPlainValue.substring(0, VERSION_LENGTH);
        if (!HASH_VERSION.equals(hashVersion)) {
            throw new IllegalArgumentException("The given version is not recognized: " + hashVersion);
        }

        String tokenPlainHexValue = tokenPlainValue.substring(VERSION_LENGTH);
        tokenPlainHexValue = tokenPlainHexValue.toLowerCase();
        if (!CHECK_32_HEX_CHAR.matcher(tokenPlainHexValue).matches()) {
            throw new IllegalArgumentException("The secret part of the token must consist of 32 hex-characters");
        }

        // legacy and version 11 hashes are both the SHA-256 of the 32 hex characters, so this finds either kind of entry
        if (rejectKnownSecret && containsSecret(plainSecretToHashBytes(tokenPlainHexValue))) {
            throw new ApiTokenPolicyViolationException(ApiTokenPolicyViolationException.Reason.SECRET_ALREADY_REGISTERED);
        }

        HashedToken token = prepareAndStoreToken(name, tokenPlainHexValue, expirationDate);

        return token.uuid;
    }

    private @NonNull HashedToken prepareAndStoreToken(@NonNull String name, @NonNull String tokenPlainValue, LocalDate expirationDate) {
        String secretValueHashed = this.plainSecretToHashInHex(tokenPlainValue);

        HashValue hashValue = new HashValue(HASH_VERSION, secretValueHashed);
        HashedToken token = HashedToken.buildNew(name, hashValue, expirationDate);

        this.addToken(token);
        return token;
    }

    private @NonNull String plainSecretToHashInHex(@NonNull String secretValueInPlainText) {
        byte[] hashBytes = plainSecretToHashBytes(secretValueInPlainText);
        return Util.toHexString(hashBytes);
    }

    private @NonNull byte[] plainSecretToHashBytes(@NonNull String secretValueInPlainText) {
        // ascii is sufficient for hex-format
        return hashedBytes(secretValueInPlainText.getBytes(StandardCharsets.US_ASCII));
    }

    private @NonNull byte[] hashedBytes(byte[] tokenBytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("There is no " + HASH_ALGORITHM + " available in this system", e);
        }
        return digest.digest(tokenBytes);
    }

    /**
     * Search in the store if there is a token with the same secret as the one given
     * @return {@code null} iff there is no matching token
     */
    public synchronized @CheckForNull HashedToken findMatchingToken(@NonNull String token) {
        String plainToken;
        if (isLegacyToken(token)) {
            plainToken = token;
        } else {
            plainToken = getHashOfToken(token);
        }

        return searchMatch(plainToken);
    }

    /**
     * Determine if the given token was generated by the legacy system or the new one
     */
    private boolean isLegacyToken(@NonNull String token) {
        return token.length() != TOKEN_LENGTH_V2;
    }

    /**
     * Retrieve the hash part of the token
     * @param token assumed the token is not a legacy one and represent the full token (version + hash)
     * @return the hash part
     */
    private @NonNull String getHashOfToken(@NonNull String token) {
        /*
         * Structure of the token:
         *
         * [2: version][32: real token]
         * ------------^^^^^^^^^^^^^^^^
         */
        return token.substring(2);
    }

    /**
     * Search in the store if there is a matching token that has the same secret.
     * @return {@code null} iff there is no matching token
     */
    private @CheckForNull HashedToken searchMatch(@NonNull String plainSecret) {
        byte[] hashedBytes = plainSecretToHashBytes(plainSecret);
        for (HashedToken token : tokenList) {
            if (token.match(hashedBytes)) {
                return token;
            }
        }

        return null;
    }

    /**
     * Whether any entry of the store has this secret, expired or not.
     * Like {@link #searchMatch}, it expects the caller to hold the lock.
     */
    private boolean containsSecret(@NonNull byte[] hashedBytes) {
        for (HashedToken token : tokenList) {
            if (token.hasSameSecret(hashedBytes)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remove a token given its identifier. Effectively make it unusable for future connection.
     *
     * @param tokenUuid The identifier of the token, could be retrieved directly from the {@link HashedToken#getUuid()}
     * @return the revoked token corresponding to the given {@code tokenUuid} if one was found, otherwise {@code null}
     */
    public synchronized @CheckForNull HashedToken revokeToken(@NonNull String tokenUuid) {
        for (Iterator<HashedToken> iterator = tokenList.iterator(); iterator.hasNext(); ) {
            HashedToken token = iterator.next();
            if (token.uuid.equals(tokenUuid)) {
                iterator.remove();

                return token;
            }
        }

        return null;
    }

    public synchronized void revokeAllTokens() {
        tokenList.clear();
    }

    public synchronized void revokeAllTokensExcept(@NonNull String tokenUuid) {
        tokenList.removeIf(token -> !token.uuid.equals(tokenUuid));
    }

    /**
     * Whether some secret is held by two or more entries that can still be used on {@code today}, that is entries that have not
     * expired. Expired entries do not count: they cannot authenticate, so an expired entry that holds a secret does not make a
     * live entry that holds it too a shared one.
     * <p>
     * Entries are compared by their hash, whatever their version is: a legacy token and a token of the current version that
     * were given the same secret hold one secret between them. An entry whose stored hash cannot be decoded holds no secret
     * and is ignored, as {@link HashedToken#match} does.
     * <p>
     * It is a single pass over the entries that stops at the second live entry of a secret, for a caller that only needs to
     * know whether there is something to report, as opposed to {@link #getUuidsSharingSecret()}.
     *
     * @param today the day that decides which entries have expired. It is a parameter, and the clock is not read, so that the
     *              caller uses one day for everything it does, and tests can choose it.
     */
    @Restricted(NoExternalUse.class)
    public synchronized boolean hasLiveSharedSecret(@NonNull LocalDate today) {
        Set<ByteBuffer> liveSecrets = new HashSet<>();
        for (HashedToken token : tokenList) {
            if (token.isExpired(today)) {
                continue;
            }
            ByteBuffer secret = token.secretKey();
            if (secret != null && !liveSecrets.add(secret)) {
                return true;
            }
        }

        return false;
    }

    /**
     * The entries of this store that share their secret with other entries, for the page that lists them.
     * <p>
     * Every entry that has the same secret as at least one other entry, expired or not, whatever its version is, is a key
     * of the result, and its value is the UUIDs of the other entries of its group, in the order of the store. An entry that
     * does not share its secret is not in the result. An entry whose stored hash cannot be decoded holds no secret and is
     * ignored, as {@link HashedToken#match} does. The hashes themselves are not exposed.
     * <p>
     * The result is a snapshot that nothing changes.
     */
    @Restricted(NoExternalUse.class)
    public synchronized @NonNull Map<String, List<String>> getUuidsSharingSecret() {
        Map<ByteBuffer, List<HashedToken>> groups = new LinkedHashMap<>();
        for (HashedToken token : tokenList) {
            ByteBuffer secret = token.secretKey();
            if (secret != null) {
                groups.computeIfAbsent(secret, key -> new ArrayList<>()).add(token);
            }
        }

        Map<String, List<String>> uuidsSharingSecret = new LinkedHashMap<>();
        for (List<HashedToken> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (HashedToken member : group) {
                List<String> others = new ArrayList<>(group.size() - 1);
                for (HashedToken other : group) {
                    if (other != member) {
                        others.add(other.uuid);
                    }
                }
                uuidsSharingSecret.put(member.uuid, Collections.unmodifiableList(others));
            }
        }

        return Collections.unmodifiableMap(uuidsSharingSecret);
    }

    /**
     * Removes the entry with the UUID {@code uuid}, whether it has expired or not, and every other entry that can still be used
     * on {@code today} and has the same secret, all of it in one operation. An entry that another thread adds with the same
     * secret is either added before, and removed with the others, or after, and kept: no live entry with the secret that was
     * in the store when this ran is left.
     * <p>
     * The entries that have expired and hold the secret are kept. They cannot authenticate, and while they are in the store
     * they stop the secret from being registered again, see {@link #addFixedNewTokenRejectingKnownSecret}.
     * <p>
     * This is what removes a secret from a store, as {@link #revokeToken} removes one entry: a caller that revokes an entry
     * whose secret is shared does not have to decide beforehand, which could be out of date once it has the lock.
     * Revoking an entry that holds a secret on its own removes that entry only. An entry whose stored hash cannot be decoded
     * holds no secret, so only that entry is removed. The entries are grouped as in {@link #getUuidsSharingSecret()}.
     * <p>
     * The caller is responsible for what depends on the entries it gets back, such as their usage statistics, and for the
     * secret of the legacy token if one of them is the legacy token.
     *
     * @param today the day that decides which entries have expired
     * @return the entries that were removed, in the order of the store, none if there is no entry with that UUID, in which
     *         case nothing was changed
     */
    @Restricted(NoExternalUse.class)
    public synchronized @NonNull List<HashedToken> revokeLiveEntriesSharingSecretWith(@NonNull String uuid, @NonNull LocalDate today) {
        HashedToken target = findByUuid(uuid);
        if (target == null) {
            return List.of();
        }

        List<HashedToken> revoked = new ArrayList<>();
        for (HashedToken token : entriesHoldingTheSecretOf(target)) {
            if (token == target || !token.isExpired(today)) {
                revoked.add(token);
            }
        }
        // HashedToken does not override equals, so these are the very entries that were found
        tokenList.removeAll(revoked);

        return revoked;
    }

    /**
     * Makes the expiration of an entry earlier. It never makes it later: the entry is changed only when {@code newExpiry} is
     * strictly before the expiration it has. An entry that has no expiration counts as one that never expires, so any date is
     * earlier and is accepted.
     * <p>
     * The legacy token is not changed: it has no expiration, and cannot be given one. A date that has passed is accepted, and
     * expires the entry.
     * <p>
     * The store is not saved, that is for the caller to do, as it is for the other changes of the store.
     *
     * @return {@code true} iff the entry was changed. {@code false} if there is no entry with that UUID, if it is the
     *         legacy token, or if its expiration is already {@code newExpiry} or earlier, in which case nothing was changed
     */
    @Restricted(NoExternalUse.class)
    public synchronized boolean shortenExpiration(@NonNull String uuid, @NonNull LocalDate newExpiry) {
        HashedToken token = findByUuid(uuid);
        if (token == null) {
            return false;
        }

        return token.shortenExpirationTo(newExpiry);
    }

    /**
     * The entry with this UUID. Like {@link #searchMatch}, it expects the caller to hold the lock.
     */
    private @CheckForNull HashedToken findByUuid(@NonNull String uuid) {
        for (HashedToken token : tokenList) {
            if (token.uuid.equals(uuid)) {
                return token;
            }
        }

        return null;
    }

    /**
     * {@code target} and every other entry that has its secret, expired or not, in the order of the store. Only {@code target}
     * if its stored hash cannot be decoded. Like {@link #searchMatch}, it expects the caller to hold the lock.
     */
    private @NonNull List<HashedToken> entriesHoldingTheSecretOf(@NonNull HashedToken target) {
        ByteBuffer secret = target.secretKey();
        if (secret == null) {
            return List.of(target);
        }

        List<HashedToken> entries = new ArrayList<>();
        for (HashedToken token : tokenList) {
            if (token == target || secret.equals(token.secretKey())) {
                entries.add(token);
            }
        }

        return entries;
    }

    /**
     * Given a token identifier and a name, the system will try to find a corresponding token and rename it
     * @return {@code true} iff the token was found and the rename was successful
     */
    public synchronized boolean renameToken(@NonNull String tokenUuid, @NonNull String newName) {
        for (HashedToken token : tokenList) {
            if (token.uuid.equals(tokenUuid)) {
                token.rename(newName);
                return true;
            }
        }

        LOGGER.log(Level.FINER, "The target token for rename does not exist, for uuid = {0}, with desired name = {1}", new Object[]{tokenUuid, newName});
        return false;
    }

    @Immutable
    private static class HashValue implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Allow to distinguish tokens from different versions easily to adapt the logic
         */
        private final String version;
        /**
         * Only confidential information in this class. It's a SHA-256 hash stored in hex format
         */
        private final String hash;

        private HashValue(String version, String hash) {
            this.version = version;
            this.hash = hash;
        }
    }

    public static class HashedToken implements Serializable {

        private static final long serialVersionUID = 1L;

        // allow us to rename the token and link the statistics
        private String uuid;
        private String name;
        private Date creationDate;
        private LocalDate expirationDate;

        private HashValue value;

        private HashedToken() {
            this.init();
        }

        private Object readResolve() {
            this.init();
            return this;
        }

        private void init() {
            if (this.uuid == null) {
                this.uuid = UUID.randomUUID().toString();
            }
        }

        public static @NonNull HashedToken buildNew(@NonNull String name, @NonNull HashValue value, LocalDate expirationDate) {
            HashedToken result = new HashedToken();
            result.name = name;
            result.creationDate = new Date();

            result.value = value;
            result.expirationDate = expirationDate;

            return result;
        }

        public static @NonNull HashedToken buildNew(@NonNull String name, @NonNull HashValue value) {
            return buildNew(name, value, null);
        }

        public static @NonNull HashedToken buildNewFromLegacy(@NonNull HashValue value, boolean migrationFromExistingLegacy) {
            HashedToken result = new HashedToken();
            result.name = Messages.ApiTokenProperty_LegacyTokenName();
            if (migrationFromExistingLegacy) {
                // we do not know when the legacy token was created
                result.creationDate = null;
            } else {
                // it comes from a manual action, so we set the creation date to now
                result.creationDate = new Date();
            }

            result.value = value;

            return result;
        }

        public void rename(String newName) {
            this.name = newName;
        }

        public boolean isAboutToExpire() {
            if (expirationDate == null) {
                return false;
            }
            LocalDate warnLimit = LocalDate.now().plusDays(7);
            return expirationDate.isBefore(warnLimit);
        }

        public boolean isExpired() {
            return isExpired(LocalDate.now());
        }

        /**
         * Like {@link #isExpired()}, for the given day instead of today, so that one day can be used for a whole operation.
         * An entry is valid through its expiration date, and one that has none never expires.
         */
        boolean isExpired(@NonNull LocalDate today) {
            return expirationDate != null && expirationDate.isBefore(today);
        }

        public boolean match(byte[] hashedBytes) {
            byte[] hashFromHex;
            try {
                hashFromHex = Util.fromHexString(value.hash);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.INFO, "The API token with name=[{0}] is not in hex-format and so cannot be used", name);
                return false;
            }

            boolean expired = isExpired();
            // String.equals() is not constant-time but this method is. No link between correctness and time spent
            return MessageDigest.isEqual(hashFromHex, hashedBytes) && !expired;
        }

        /**
         * Like {@link #match(byte[])}, but without looking at the expiration: whether this entry holds the secret that
         * has this hash, whether it can still be used or not. The version of the entry is not compared either: the
         * hash of a legacy token and that of a version 11 token are both the SHA-256 of the 32 hexadecimal characters
         * of the secret, so the same secret gives the same hash for both.
         *
         * @param hashedBytes the SHA-256 of the lowercase hexadecimal secret
         * @return {@code false} if this entry has another secret, or if its stored hash is not valid hexadecimal
         */
        boolean hasSameSecret(byte[] hashedBytes) {
            byte[] hashFromHex;
            try {
                hashFromHex = Util.fromHexString(value.hash);
            } catch (IllegalArgumentException e) {
                // includes NumberFormatException, which is what match catches: an entry that cannot be decoded has no secret to compare
                LOGGER.log(Level.FINE, "The API token with uuid=[{0}] is not in hex-format, so it is ignored when looking for a known secret", uuid);
                return false;
            }

            // String.equals() is not constant-time but this method is. No link between correctness and time spent
            return MessageDigest.isEqual(hashFromHex, hashedBytes);
        }

        /**
         * The stored hash, decoded, to find the entries that have the same secret: two entries have the same secret when their
         * keys are equal, whatever their version is, as for {@link #hasSameSecret}. These are hashes that are stored, not a
         * value that someone submits, so comparing them is not a timing concern.
         *
         * @return {@code null} if the stored hash is not hexadecimal, or is not the 32 bytes of a SHA-256: this entry holds no
         *         secret that {@link #match} can ever accept, so it is not compared with the others
         */
        private @CheckForNull ByteBuffer secretKey() {
            byte[] hashFromHex;
            try {
                hashFromHex = Util.fromHexString(value.hash);
            } catch (IllegalArgumentException e) {
                // includes NumberFormatException, which is what match catches
                LOGGER.log(Level.FINE, "The API token with uuid=[{0}] is not in hex-format, so it holds no secret to compare with the others", uuid);
                return null;
            }
            if (hashFromHex.length != SHA_256_LENGTH) {
                LOGGER.log(Level.FINE, "The API token with uuid=[{0}] has a hash that is not a {1}, so it holds no secret to compare with the others", new Object[]{uuid, HASH_ALGORITHM});
                return null;
            }

            return ByteBuffer.wrap(hashFromHex);
        }

        /**
         * Makes the expiration of this entry earlier. Never later: the expiration is changed only when {@code newExpiry} is
         * strictly before the current one, and an entry without expiration counts as one that never expires, so any date is
         * earlier. It is not changed for the legacy token, which has no expiration and cannot be given one.
         * The caller must hold the lock of the store, and has to save what it changed.
         *
         * @return {@code true} iff the expiration was changed
         */
        boolean shortenExpirationTo(@NonNull LocalDate newExpiry) {
            Objects.requireNonNull(newExpiry);
            if (isLegacy()) {
                return false;
            }
            if (expirationDate != null && !newExpiry.isBefore(expirationDate)) {
                return false;
            }

            expirationDate = newExpiry;
            return true;
        }

        // used by Jelly view
        public String getName() {
            return name;
        }

        // used by Jelly view
        public Date getCreationDate() {
            return creationDate;
        }

        public LocalDate getExpirationDate() {
            return expirationDate;
        }

        // used by Jelly view
        /**
         * Relevant only if the lastUseDate is not null
         */
        public long getNumDaysCreation() {
            return creationDate == null ? 0 : Util.daysElapsedSince(creationDate);
        }

        // used by Jelly view
        public String getUuid() {
            return this.uuid;
        }

        public boolean isLegacy() {
            return this.value.version.equals(LEGACY_VERSION);
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
