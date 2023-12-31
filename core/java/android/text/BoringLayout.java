/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.style.ParagraphStyle;

/**
 * A BoringLayout is a very simple Layout implementation for text that
 * fits on a single line and is all left-to-right characters.
 * You will probably never want to make one of these yourself;
 * if you do, be sure to call {@link #isBoring} first to make sure
 * the text meets the criteria.
 * <p>This class is used by widgets to control text layout. You should not need
 * to use this class directly unless you are implementing your own widget
 * or custom display object, in which case
 * you are encouraged to use a Layout instead of calling
 * {@link android.graphics.Canvas#drawText(java.lang.CharSequence, int, int, float, float, android.graphics.Paint)
 *  Canvas.drawText()} directly.</p>
 */
public class BoringLayout extends Layout implements TextUtils.EllipsizeCallback {

    /**
     * Utility function to construct a BoringLayout instance.
     *
     * @param source the text to render
     * @param paint the default paint for the layout
     * @param outerWidth the wrapping width for the text
     * @param align whether to left, right, or center the text
     * @param spacingMult this value is no longer used by BoringLayout
     * @param spacingAdd this value is no longer used by BoringLayout
     * @param metrics {@code #Metrics} instance that contains information about FontMetrics and
     *                line width
     * @param includePad set whether to include extra space beyond font ascent and descent which is
     *                   needed to avoid clipping in some scripts
     */
    public static BoringLayout make(CharSequence source, TextPaint paint, int outerWidth,
            Alignment align, float spacingMult, float spacingAdd, BoringLayout.Metrics metrics,
            boolean includePad) {
        return new BoringLayout(source, paint, outerWidth, align, spacingMult, spacingAdd, metrics,
                includePad);
    }

    /**
     * Utility function to construct a BoringLayout instance.
     *
     * @param source the text to render
     * @param paint the default paint for the layout
     * @param outerWidth the wrapping width for the text
     * @param align whether to left, right, or center the text
     * @param spacingmult this value is no longer used by BoringLayout
     * @param spacingadd this value is no longer used by BoringLayout
     * @param metrics {@code #Metrics} instance that contains information about FontMetrics and
     *                line width
     * @param includePad set whether to include extra space beyond font ascent and descent which is
     *                   needed to avoid clipping in some scripts
     * @param ellipsize whether to ellipsize the text if width of the text is longer than the
     *                  requested width
     * @param ellipsizedWidth the width to which this Layout is ellipsizing. If {@code ellipsize} is
     *                        {@code null}, or is {@link TextUtils.TruncateAt#MARQUEE} this value is
     *                        not used, {@code outerWidth} is used instead
     */
    public static BoringLayout make(CharSequence source, TextPaint paint, int outerWidth,
            Alignment align, float spacingmult, float spacingadd, BoringLayout.Metrics metrics,
            boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        return new BoringLayout(source, paint, outerWidth, align, spacingmult, spacingadd, metrics,
                includePad, ellipsize, ellipsizedWidth);
    }

    /**
     * Returns a BoringLayout for the specified text, potentially reusing
     * this one if it is already suitable.  The caller must make sure that
     * no one is still using this Layout.
     *
     * @param source the text to render
     * @param paint the default paint for the layout
     * @param outerwidth the wrapping width for the text
     * @param align whether to left, right, or center the text
     * @param spacingMult this value is no longer used by BoringLayout
     * @param spacingAdd this value is no longer used by BoringLayout
     * @param metrics {@code #Metrics} instance that contains information about FontMetrics and
     *                line width
     * @param includePad set whether to include extra space beyond font ascent and descent which is
     *                   needed to avoid clipping in some scripts
     */
    public BoringLayout replaceOrMake(CharSequence source, TextPaint paint, int outerwidth,
            Alignment align, float spacingMult, float spacingAdd, BoringLayout.Metrics metrics,
            boolean includePad) {
        replaceWith(source, paint, outerwidth, align, spacingMult, spacingAdd);

        mEllipsizedWidth = outerwidth;
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;

        init(source, paint, align, metrics, includePad, true);
        return this;
    }

    /**
     * Returns a BoringLayout for the specified text, potentially reusing
     * this one if it is already suitable.  The caller must make sure that
     * no one is still using this Layout.
     *
     * @param source the text to render
     * @param paint the default paint for the layout
     * @param outerWidth the wrapping width for the text
     * @param align whether to left, right, or center the text
     * @param spacingMult this value is no longer used by BoringLayout
     * @param spacingAdd this value is no longer used by BoringLayout
     * @param metrics {@code #Metrics} instance that contains information about FontMetrics and
     *                line width
     * @param includePad set whether to include extra space beyond font ascent and descent which is
     *                   needed to avoid clipping in some scripts
     * @param ellipsize whether to ellipsize the text if width of the text is longer than the
     *                  requested width
     * @param ellipsizedWidth the width to which this Layout is ellipsizing. If {@code ellipsize} is
     *                        {@code null}, or is {@link TextUtils.TruncateAt#MARQUEE} this value is
     *                        not used, {@code outerwidth} is used instead
     */
    public BoringLayout replaceOrMake(CharSequence source, TextPaint paint, int outerWidth,
            Alignment align, float spacingMult, float spacingAdd, BoringLayout.Metrics metrics,
            boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        boolean trust;

        if (ellipsize == null || ellipsize == TextUtils.TruncateAt.MARQUEE) {
            replaceWith(source, paint, outerWidth, align, spacingMult, spacingAdd);

            mEllipsizedWidth = outerWidth;
            mEllipsizedStart = 0;
            mEllipsizedCount = 0;
            trust = true;
        } else {
            replaceWith(TextUtils.ellipsize(source, paint, ellipsizedWidth, ellipsize, true, this),
                    paint, outerWidth, align, spacingMult, spacingAdd);

            mEllipsizedWidth = ellipsizedWidth;
            trust = false;
        }

        init(getText(), paint, align, metrics, includePad, trust);
        return this;
    }

    /**
     * @param source the text to render
     * @param paint the default paint for the layout
     * @param outerwidth the wrapping width for the text
     * @param align whether to left, right, or center the text
     * @param spacingMult this value is no longer used by BoringLayout
     * @param spacingAdd this value is no longer used by BoringLayout
     * @param metrics {@code #Metrics} instance that contains information about FontMetrics and
     *                line width
     * @param includePad set whether to include extra space beyond font ascent and descent which is
     *                   needed to avoid clipping in some scripts
     */
    public BoringLayout(CharSequence source, TextPaint paint, int outerwidth, Alignment align,
            float spacingMult, float spacingAdd, BoringLayout.Metrics metrics, boolean includePad) {
        super(source, paint, outerwidth, align, spacingMult, spacingAdd);

        mEllipsizedWidth = outerwidth;
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;

        init(source, paint, align, metrics, includePad, true);
    }

    /**
     *
     * @param source the text to render
     * @param paint the default paint for the layout
     * @param outerWidth the wrapping width for the text
     * @param align whether to left, right, or center the text
     * @param spacingMult this value is no longer used by BoringLayout
     * @param spacingAdd this value is no longer used by BoringLayout
     * @param metrics {@code #Metrics} instance that contains information about FontMetrics and
     *                line width
     * @param includePad set whether to include extra space beyond font ascent and descent which is
     *                   needed to avoid clipping in some scripts
     * @param ellipsize whether to ellipsize the text if width of the text is longer than the
     *                  requested {@code outerwidth}
     * @param ellipsizedWidth the width to which this Layout is ellipsizing. If {@code ellipsize} is
     *                        {@code null}, or is {@link TextUtils.TruncateAt#MARQUEE} this value is
     *                        not used, {@code outerwidth} is used instead
     */
    public BoringLayout(CharSequence source, TextPaint paint, int outerWidth, Alignment align,
            float spacingMult, float spacingAdd, BoringLayout.Metrics metrics, boolean includePad,
            TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        /*
         * It is silly to have to call super() and then replaceWith(),
         * but we can't use "this" for the callback until the call to
         * super() finishes.
         */
        super(source, paint, outerWidth, align, spacingMult, spacingAdd);

        boolean trust;

        if (ellipsize == null || ellipsize == TextUtils.TruncateAt.MARQUEE) {
            mEllipsizedWidth = outerWidth;
            mEllipsizedStart = 0;
            mEllipsizedCount = 0;
            trust = true;
        } else {
            replaceWith(TextUtils.ellipsize(source, paint, ellipsizedWidth, ellipsize, true, this),
                        paint, outerWidth, align, spacingMult, spacingAdd);

            mEllipsizedWidth = ellipsizedWidth;
            trust = false;
        }

        init(getText(), paint, align, metrics, includePad, trust);
    }

    /* package */ void init(CharSequence source, TextPaint paint, Alignment align,
            BoringLayout.Metrics metrics, boolean includePad, boolean trustWidth) {
        int spacing;

        if (source instanceof String && align == Layout.Alignment.ALIGN_NORMAL) {
            mDirect = source.toString();
        } else {
            mDirect = null;
        }

        mPaint = paint;

        if (includePad) {
            spacing = metrics.bottom - metrics.top;
            mDesc = metrics.bottom;
        } else {
            spacing = metrics.descent - metrics.ascent;
            mDesc = metrics.descent;
        }

        mBottom = spacing;

        if (trustWidth) {
            mMax = metrics.width;
        } else {
            /*
             * If we have ellipsized, we have to actually calculate the
             * width because the width that was passed in was for the
             * full text, not the ellipsized form.
             */
            TextLine line = TextLine.obtain();
            line.set(paint, source, 0, source.length(), Layout.DIR_LEFT_TO_RIGHT,
                    Layout.DIRS_ALL_LEFT_TO_RIGHT, false, null);
            mMax = (int) Math.ceil(line.metrics(null));
            TextLine.recycle(line);
        }

        if (includePad) {
            mTopPadding = metrics.top - metrics.ascent;
            mBottomPadding = metrics.bottom - metrics.descent;
        }
    }

    /**
     * Returns null if not boring; the width, ascent, and descent if boring.
     */
    public static Metrics isBoring(CharSequence text, TextPaint paint) {
        return isBoring(text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, null);
    }

    /**
     * Returns null if not boring; the width, ascent, and descent in the
     * provided Metrics object (or a new one if the provided one was null)
     * if boring.
     */
    public static Metrics isBoring(CharSequence text, TextPaint paint, Metrics metrics) {
        return isBoring(text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, metrics);
    }

    /**
     * Returns true if the text contains any RTL characters, bidi format characters, or surrogate
     * code units.
     */
    private static boolean hasAnyInterestingChars(CharSequence text, int textLength) {
        final int MAX_BUF_LEN = 500;
        final char[] buffer = TextUtils.obtain(MAX_BUF_LEN);
        try {
            for (int start = 0; start < textLength; start += MAX_BUF_LEN) {
                final int end = Math.min(start + MAX_BUF_LEN, textLength);

                // No need to worry about getting half codepoints, since we consider surrogate code
                // units "interesting" as soon we see one.
                TextUtils.getChars(text, start, end, buffer, 0);

                final int len = end - start;
                for (int i = 0; i < len; i++) {
                    final char c = buffer[i];
                    if (c == '\n' || c == '\t' || TextUtils.couldAffectRtl(c)) {
                        return true;
                    }
                }
            }
            return false;
        } finally {
            TextUtils.recycle(buffer);
        }
    }

    /**
     * Returns null if not boring; the width, ascent, and descent in the
     * provided Metrics object (or a new one if the provided one was null)
     * if boring.
     * @hide
     */
    public static Metrics isBoring(CharSequence text, TextPaint paint,
            TextDirectionHeuristic textDir, Metrics metrics) {
        final int textLength = text.length();
        if (hasAnyInterestingChars(text, textLength)) {
           return null;  // There are some interesting characters. Not boring.
        }
        if (textDir != null && textDir.isRtl(text, 0, textLength)) {
           return null;  // The heuristic considers the whole text RTL. Not boring.
        }
        if (text instanceof Spanned) {
            Spanned sp = (Spanned) text;
            Object[] styles = sp.getSpans(0, textLength, ParagraphStyle.class);
            if (styles.length > 0) {
                return null;  // There are some PargraphStyle spans. Not boring.
            }
        }

        Metrics fm = metrics;
        if (fm == null) {
            fm = new Metrics();
        } else {
            fm.reset();
        }

        TextLine line = TextLine.obtain();
        line.set(paint, text, 0, textLength, Layout.DIR_LEFT_TO_RIGHT,
                Layout.DIRS_ALL_LEFT_TO_RIGHT, false, null);
        if (text instanceof PrecomputedText) {
            PrecomputedText mt = (PrecomputedText) text;
            // Reaching here means there is only one paragraph.
            MeasuredParagraph mp = mt.getMeasuredParagraph(0);
            fm.width = (int) Math.ceil(mp.getWidth(0, mp.getTextLength()));
        } else {
            fm.width = (int) Math.ceil(line.metrics(fm));
        }
        TextLine.recycle(line);

        return fm;
    }

    @Override
    public int getHeight() {
        return mBottom;
    }

    @Override
    public int getLineCount() {
        return 1;
    }

    @Override
    public int getLineTop(int line) {
        if (line == 0)
            return 0;
        else
            return mBottom;
    }

    @Override
    public int getLineDescent(int line) {
        return mDesc;
    }

    @Override
    public int getLineStart(int line) {
        if (line == 0)
            return 0;
        else
            return getText().length();
    }

    @Override
    public int getParagraphDirection(int line) {
        return DIR_LEFT_TO_RIGHT;
    }

    @Override
    public boolean getLineContainsTab(int line) {
        return false;
    }

    @Override
    public float getLineMax(int line) {
        return mMax;
    }

    @Override
    public float getLineWidth(int line) {
        return (line == 0 ? mMax : 0);
    }

    @Override
    public final Directions getLineDirections(int line) {
        return Layout.DIRS_ALL_LEFT_TO_RIGHT;
    }

    @Override
    public int getTopPadding() {
        return mTopPadding;
    }

    @Override
    public int getBottomPadding() {
        return mBottomPadding;
    }

    @Override
    public int getEllipsisCount(int line) {
        return mEllipsizedCount;
    }

    @Override
    public int getEllipsisStart(int line) {
        return mEllipsizedStart;
    }

    @Override
    public int getEllipsizedWidth() {
        return mEllipsizedWidth;
    }

    // Override draw so it will be faster.
    @Override
    public void draw(Canvas c, Path highlight, Paint highlightpaint,
                     int cursorOffset) {
        if (mDirect != null && highlight == null) {
            c.drawText(mDirect, 0, mBottom - mDesc, mPaint);
        } else {
            super.draw(c, highlight, highlightpaint, cursorOffset);
        }
    }

    /**
     * Callback for the ellipsizer to report what region it ellipsized.
     */
    public void ellipsized(int start, int end) {
        mEllipsizedStart = start;
        mEllipsizedCount = end - start;
    }

    private String mDirect;
    private Paint mPaint;

    /* package */ int mBottom, mDesc;   // for Direct
    private int mTopPadding, mBottomPadding;
    private float mMax;
    private int mEllipsizedWidth, mEllipsizedStart, mEllipsizedCount;

    public static class Metrics extends Paint.FontMetricsInt {
        public int width;

        @Override public String toString() {
            return super.toString() + " width=" + width;
        }

        private void reset() {
            top = 0;
            bottom = 0;
            ascent = 0;
            descent = 0;
            width = 0;
            leading = 0;
        }
    }
}
