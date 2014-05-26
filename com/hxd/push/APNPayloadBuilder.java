package com.hxd.push;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class APNPayloadBuilder {

    private static final int MAX_PAYLOAD_LENGTH = 256;
    private final Map<String, Object> root;
    private final Map<String, Object> aps;
    private final Map<String, Object> customAlert;

    public APNPayloadBuilder() {
        root = new HashMap<String, Object>();
        aps = new HashMap<String, Object>();
        customAlert = new HashMap<String, Object>();
    }


    public APNPayloadBuilder alertBody(final String alert) {
        customAlert.put("body", alert);
        return this;
    }

    public APNPayloadBuilder sound(final String sound) {
        if (sound != null) {
            aps.put("sound", sound);
        } else {
            aps.remove("sound");
        }
        return this;
    }


    public APNPayloadBuilder badge(final int badge) {
        aps.put("badge", badge);
        return this;
    }


    public APNPayloadBuilder clearBadge() {
        return badge(0);
    }


    public APNPayloadBuilder actionKey(final String actionKey) {
        customAlert.put("action-loc-key", actionKey);
        return this;
    }

    /**
     * Set the notification view to display an action button.
     *
     * This is an alias to {@code actionKey(null)}
     *
     * @return this
     */
    public APNPayloadBuilder noActionButton() {
        return actionKey(null);
    }


    public APNPayloadBuilder forNewsstand() {
        aps.put("content-available", 1);
        return this;
    }


    public APNPayloadBuilder localizedKey(final String key) {
        customAlert.put("loc-key", key);
        return this;
    }


    public APNPayloadBuilder localizedArguments(final Collection<String> arguments) {
        customAlert.put("loc-args", arguments);
        return this;
    }


    public APNPayloadBuilder localizedArguments(final String... arguments) {
        return localizedArguments(Arrays.asList(arguments));
    }

    /**
     * Sets the launch image file for the push notification
     *
     * @param launchImage   the filename of the image file in the
     *      application bundle.
     * @return  this
     */
    public APNPayloadBuilder launchImage(final String launchImage) {
        customAlert.put("launch-image", launchImage);
        return this;
    }

    /**
     * Sets any application-specific custom fields.  The values
     * are presented to the application and the iPhone doesn't
     * display them automatically.
     *
     * This can be used to pass specific values (urls, ids, etc) to
     * the application in addition to the notification message
     * itself.
     *
     * @param key   the custom field name
     * @param value the custom field value
     * @return  this
     */
    public APNPayloadBuilder customField(final String key, final Object value) {
        root.put(key, value);
        return this;
    }

    public APNPayloadBuilder mdm(final String s) {
        return customField("mdm", s);
    }

    /**
     * Set any application-specific custom fields.  These values
     * are presented to the application and the iPhone doesn't
     * display them automatically.
     *
     * This method *adds* the custom fields in the map to the
     * payload, and subsequent calls add but doesn't reset the
     * custom fields.
     *
     * @param map   the custom map
     * @return  this
     */
    public APNPayloadBuilder customFields(final Map<String, ? extends Object> values) {
        root.putAll(values);
        return this;
    }

    /**
     * Returns the length of payload bytes once marshaled to bytes
     *
     * @return the length of the payload
     */
    public int length() {
        return copy().buildBytes().length;
    }

    /**
     * Returns true if the payload built so far is larger than
     * the size permitted by Apple (which is 256 bytes).
     *
     * @return true if the result payload is too long
     */
    public boolean isTooLong() {
        return length() > MAX_PAYLOAD_LENGTH;
    }

    /**
     * Shrinks the alert message body so that the resulting payload
     * message fits within the passed expected payload length.
     *
     * This method performs best-effort approach, and its behavior
     * is unspecified when handling alerts where the payload
     * without body is already longer than the permitted size, or
     * if the break occurs within word.
     *
     * @param payloadLength the expected max size of the payload
     * @return  this
     */
    public APNPayloadBuilder resizeAlertBody(final int payloadLength) {
        return resizeAlertBody(payloadLength, "");
    }

    /**
     * Shrinks the alert message body so that the resulting payload
     * message fits within the passed expected payload length.
     *
     * This method performs best-effort approach, and its behavior
     * is unspecified when handling alerts where the payload
     * without body is already longer than the permitted size, or
     * if the break occurs within word.
     *
     * @param payloadLength the expected max size of the payload
     * @param postfix for the truncated body, e.g. "..."
     * @return  this
     */
    public APNPayloadBuilder resizeAlertBody(final int payloadLength, final String postfix) {
        int currLength = length();
        if (currLength <= payloadLength) {
            return this;
        }

        // now we are sure that truncation is required
        String body = (String)customAlert.get("body");

        final int acceptableSize = APNPayloadBuilder.toUTF8Bytes(body).length
                - (currLength - payloadLength
                        + APNPayloadBuilder.toUTF8Bytes(postfix).length);
        body = APNPayloadBuilder.truncateWhenUTF8(body, acceptableSize) + postfix;

        // set it back
        customAlert.put("body", body);

        // calculate the length again
        currLength = length();

        if(currLength > payloadLength) {
            // string is still too long, just remove the body as the body is
            // anyway not the cause OR the postfix might be too long
            customAlert.remove("body");
        }

        return this;
    }
    
    public static String truncateWhenUTF8(final String s, final int maxBytes) {
        int b = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);

            // ranges from http://en.wikipedia.org/wiki/UTF-8
            int skip = 0;
            int more;
            if (c <= 0x007f) {
                more = 1;
            }
            else if (c <= 0x07FF) {
                more = 2;
            } else if (c <= 0xd7ff) {
                more = 3;
            } else if (c <= 0xDFFF) {
                // surrogate area, consume next char as well
                more = 4;
                skip = 1;
            } else {
                more = 3;
            }

            if (b + more > maxBytes) {
                return s.substring(0, i);
            }
            b += more;
            i += skip;
        }
        return s;
    }
    
    public static byte[] toUTF8Bytes(final String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Shrinks the alert message body so that the resulting payload
     * message fits within require Apple specification (256 bytes).
     *
     * This method performs best-effort approach, and its behavior
     * is unspecified when handling alerts where the payload
     * without body is already longer than the permitted size, or
     * if the break occurs within word.
     *
     * @return  this
     */
    public APNPayloadBuilder shrinkBody() {
        return shrinkBody("");
    }

    /**
     * Shrinks the alert message body so that the resulting payload
     * message fits within require Apple specification (256 bytes).
     *
     * This method performs best-effort approach, and its behavior
     * is unspecified when handling alerts where the payload
     * without body is already longer than the permitted size, or
     * if the break occurs within word.
     *
     * @param postfix for the truncated body, e.g. "..."
     *
     * @return  this
     */
    public APNPayloadBuilder shrinkBody(final String postfix) {
        return resizeAlertBody(MAX_PAYLOAD_LENGTH, postfix);
    }

    /**
     * Returns the JSON String representation of the payload
     * according to Apple APNS specification
     *
     * @return  the String representation as expected by Apple
     */
    public String build() {
        if (!root.containsKey("mdm")) {
            insertCustomAlert();
            root.put("aps", aps);
        }
        try {
            return new Gson().toJson(root);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertCustomAlert() {
        switch (customAlert.size()) {
            case 0:
                aps.remove("alert");
                break;
            case 1:
                if (customAlert.containsKey("body")) {
                    aps.put("alert", customAlert.get("body"));
                    break;
                }
                // else follow through
                //$FALL-THROUGH$
            default:
                aps.put("alert", customAlert);
        }
    }

    /**
     * Returns the bytes representation of the payload according to
     * Apple APNS specification
     *
     * @return the bytes as expected by Apple
     */
    public byte[] buildBytes() {
        return APNPayloadBuilder.toUTF8Bytes(build());
    }

    @Override
    public String toString() {
        return build();
    }

    private APNPayloadBuilder(final Map<String, Object> root,
            final Map<String, Object> aps,
            final Map<String, Object> customAlert) {
        this.root = new HashMap<String, Object>(root);
        this.aps = new HashMap<String, Object>(aps);
        this.customAlert = new HashMap<String, Object>(customAlert);
    }

    /**
     * Returns a copy of this builder
     *
     * @return a copy of this builder
     */
    public APNPayloadBuilder copy() {
        return new APNPayloadBuilder(root, aps, customAlert);
    }

    /**
     * @return a new instance of Payload Builder
     */
    public static APNPayloadBuilder newPayload() {
        return new APNPayloadBuilder();
    }

}
