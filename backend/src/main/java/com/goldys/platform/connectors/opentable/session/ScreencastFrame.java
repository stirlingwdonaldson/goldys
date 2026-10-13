package com.goldys.platform.connectors.opentable.session;

/**
 * One JPEG frame of the live view, still base64-encoded as CDP delivered it (it is forwarded to the
 * browser as-is). {@code width}/{@code height} are the page's CSS viewport, which input coordinates
 * are expressed in.
 */
public record ScreencastFrame(String jpegBase64, int width, int height) {}
