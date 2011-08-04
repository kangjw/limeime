/*    
**    Copyright 2010, The LimeIME Open Source Project
** 
**    Project Url: http://code.google.com/p/limeime/
**                 http://android.toload.net/
**
**    This program is free software: you can redistribute it and/or modify
**    it under the terms of the GNU General Public License as published by
**    the Free Software Foundation, either version 3 of the License, or
**    (at your option) any later version.

**    This program is distributed in the hope that it will be useful,
**    but WITHOUT ANY WARRANTY; without even the implied warranty of
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**    GNU General Public License for more details.

**    You should have received a copy of the GNU General Public License
**    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.toload.main.hd;

import java.util.Locale;

import net.toload.main.hd.R;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.text.TextPaint;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;

/**
 * @author Art Hung
 */
public class LIMEKeyboard extends Keyboard {

	static final boolean DEBUG = false;
	static final String TAG = "LIMEKeyboard";

	private Drawable mShiftLockIcon;
    private Drawable mShiftLockPreviewIcon;
    private Drawable mOldShiftIcon;
    private Drawable mOldShiftPreviewIcon;
    private Key mShiftKey;
    private Key mEnterKey;
	    
    private static final int SHIFT_OFF = 0;
    private static final int SHIFT_ON = 1;
    private static final int SHIFT_LOCKED = 2;
    
    private int mShiftState = SHIFT_OFF;

    private static final float SPACEBAR_DRAG_THRESHOLD = 0.8f;
//    private static final float OVERLAP_PERCENTAGE_LOW_PROB = 0.70f;
//    private static final float OVERLAP_PERCENTAGE_HIGH_PROB = 0.85f;
//    // Minimum width of space key preview (proportional to keyboard width)
//    private static final float SPACEBAR_POPUP_MIN_RATIO = 0.4f;
//    // Height in space key the language name will be drawn. (proportional to space key height)
//    private static final float SPACEBAR_LANGUAGE_BASELINE = 0.6f;
//    // If the full language name needs to be smaller than this value to be drawn on space key,
//    // its short language name will be used instead.
//    private static final float MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f;

    private static int sSpacebarVerticalCorrection;

    
    static final int KEYCODE_ENTER = '\n';
    static final int KEYCODE_SPACE = ' ';
    
    private boolean mCurrentlyInSpace;
    private int mSpaceDragStartX;
    private int mSpaceDragLastDiff;
    private Key mSpaceKey;
    private final Context mContext;
    private int mMode;
    private final Resources mRes;
    
    public LIMEKeyboard(Context context, int xmlLayoutResId) {
    	this(context, xmlLayoutResId, 0);
    }

//    public LIMEKeyboard(Context context, int layoutTemplateResId, 
//            CharSequence characters, int columns, int horizontalPadding) {
//        super(context, layoutTemplateResId, characters, columns, horizontalPadding);
//    }
//    
    public LIMEKeyboard(Context context, int xmlLayoutResId, int mode) {
        super(context, xmlLayoutResId, mode);
        final Resources res = context.getResources();
        mContext = context;
        mMode = mode;
        mRes = res;
        mShiftLockIcon = res.getDrawable(R.drawable.sym_keyboard_shift_locked);
        mShiftLockPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_shift_locked);
        mShiftLockPreviewIcon.setBounds(0, 0, 
                mShiftLockPreviewIcon.getIntrinsicWidth(),
                mShiftLockPreviewIcon.getIntrinsicHeight());
        sSpacebarVerticalCorrection = res.getDimensionPixelOffset(
                R.dimen.spacebar_vertical_correction);
    }
    
    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, 
            XmlResourceParser parser) {
        Key key = new LIMEKey(res, parent, x, y, parser);
        switch (key.codes[0]) {
        case KEYCODE_ENTER:
            mEnterKey = key;
            break;
        case KEYCODE_SPACE:
            mSpaceKey = key;
            break;
        }
        return key;
    }
    
    void enableShiftLock() {
        int index = getShiftKeyIndex();
        if (index >= 0) {
            mShiftKey = getKeys().get(index);
            if (mShiftKey instanceof LIMEKey) {
                ((LIMEKey)mShiftKey).enableShiftLock();
            }
            mOldShiftIcon = mShiftKey.icon;
            mOldShiftPreviewIcon = mShiftKey.iconPreview;
        }
    }

    void setShiftLocked(boolean shiftLocked) {
    	if(DEBUG) {Log.i("LIMEKeyboard", "setShiftLocked: "+ shiftLocked);};
        if (mShiftKey != null) {
            if (shiftLocked) {
                mShiftKey.on = true;
                mShiftKey.icon = mShiftLockIcon;
                mShiftState = SHIFT_LOCKED;
            } else {
                mShiftKey.on = false;
                mShiftKey.icon = mShiftLockIcon;
                mShiftState = SHIFT_ON;
            }
        }
    }

    boolean isShiftLocked() {
        return mShiftState == SHIFT_LOCKED;
    }
    
    @Override
    public boolean setShifted(boolean shiftState) {
    	if(DEBUG) {Log.i("LIMEKeyboard", "setShifted: "+ shiftState);};
        boolean shiftChanged = false;
        if (mShiftKey != null) {
            if (shiftState == false) {
                shiftChanged = mShiftState != SHIFT_OFF;
                mShiftState = SHIFT_OFF;
                mShiftKey.on = false;
                mShiftKey.icon = mOldShiftIcon;
                mShiftKey.iconPreview = mOldShiftPreviewIcon;
            } else {
                if (mShiftState == SHIFT_OFF) {
                    shiftChanged = mShiftState == SHIFT_OFF;
                    mShiftState = SHIFT_ON;
                    mShiftKey.icon = mShiftLockIcon;
                }
            }
        } else {
            return super.setShifted(shiftState);
        }
        return shiftChanged;
    }
    
    @Override
    public boolean isShifted() {
        if (mShiftKey != null) {
            return mShiftState != SHIFT_OFF;
        } else {
            return super.isShifted();
        }
    }
    
    void setImeOptions(Resources res, int options) {
    	setImeOptions(res, LIMEKeyboardSwitcher.MODE_TEXT, options);
    }
    
    void setImeOptions(Resources res, int mode, int options) {
        if (mEnterKey != null) {
            // Reset some of the rarely used attributes.
            mEnterKey.popupCharacters = null;
            mEnterKey.popupResId = 0;
            mEnterKey.text = null;
            switch (options&(EditorInfo.IME_MASK_ACTION|EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
                case EditorInfo.IME_ACTION_GO:
                    mEnterKey.iconPreview = null;
                    //mEnterKey.icon = res.getDrawable(R.drawable.sym_keyboard_return);
                    mEnterKey.label = res.getText(R.string.label_go_key);
                    break;
                case EditorInfo.IME_ACTION_NEXT:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    //int c[] = {-99};
                    //mEnterKey.codes = c;
                    mEnterKey.label = res.getText(R.string.label_next_key);
                    break;
                case EditorInfo.IME_ACTION_DONE:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_done_key);
                    break;
                case EditorInfo.IME_ACTION_SEARCH:
                    mEnterKey.iconPreview = res.getDrawable(
                            R.drawable.sym_keyboard_feedback_search);
                    mEnterKey.icon = res.getDrawable(
                            R.drawable.sym_keyboard_search);
                    mEnterKey.label = null;
                    break;
                case EditorInfo.IME_ACTION_SEND:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_send_key);
                    break;
                default:
                	
                    if (mode == LIMEKeyboardSwitcher.MODE_IM) {
                        mEnterKey.icon = null;
                        mEnterKey.iconPreview = null;
                        mEnterKey.label = ":-)";
                        //mEnterKey.text = ":-) ";
                        mEnterKey.popupResId = R.xml.popup_smileys;
                    } else {
                    
                        mEnterKey.iconPreview = res.getDrawable(
                                R.drawable.sym_keyboard_feedback_return);
                        mEnterKey.icon = res.getDrawable(
                                R.drawable.sym_keyboard_return);
                        mEnterKey.label = null;
                    }
                    break;
            }
            // Set the initial size of the preview icon
            if (mEnterKey.iconPreview != null) {
                mEnterKey.iconPreview.setBounds(0, 0, 
                        mEnterKey.iconPreview.getIntrinsicWidth(),
                        mEnterKey.iconPreview.getIntrinsicHeight());
            }
        }
        
    }
    
    /**
     * Does the magic of locking the touch gesture into the spacebar when
     * switching input languages.
     */
    boolean isInside(LIMEKey key, int x, int y) {
    	if(DEBUG) Log.i(TAG, "isInside(), keycode = " + key.codes[0] + ". x=" + x + ". y="+y + 
    				". mSpaceDragStartX=" + mSpaceDragStartX +
    				". mSpaceDragLastDiff=" + mSpaceDragLastDiff);
        final int code = key.codes[0];
        if (code == KEYCODE_SHIFT ||
                code == KEYCODE_DELETE) {
            y -= key.height / 10;
            if (code == KEYCODE_SHIFT) x += key.width / 6;
            if (code == KEYCODE_DELETE) x -= key.width / 6;
        } else if (code == KEYCODE_SPACE) {
            y += LIMEKeyboard.sSpacebarVerticalCorrection;
            
                if (mCurrentlyInSpace) {
                    int diff = x - mSpaceDragStartX;
                    if (Math.abs(diff - mSpaceDragLastDiff) > 0) {
                        //updateLocaleDrag(diff);
                    }
                    mSpaceDragLastDiff = diff;
                    return true;
                } else {
                    boolean insideSpace = key.isInsideSuper(x, y);
                    if (insideSpace) {
                        mCurrentlyInSpace = true;
                        mSpaceDragStartX = x;
                       // updateLocaleDrag(0);
                    }
                    return insideSpace;
                }
               
           
        } 

        // Lock into the spacebar
        if (mCurrentlyInSpace) return false;

        return key.isInsideSuper(x, y);
    }
    
    void keyReleased() {
        mCurrentlyInSpace = false;
        mSpaceDragLastDiff = 0;
//        mPrefLetter = 0;
//        mPrefLetterX = 0;
//        mPrefLetterY = 0;
//        mPrefDistance = Integer.MAX_VALUE;
//        if (mSpaceKey != null) {
//            updateLocaleDrag(Integer.MAX_VALUE);
//        }
    }
    
    public int getSpaceDragDirection() {
    	Log.i(TAG, "getSpaceDragDirection(): mSpaceDragLastDiff= " + 
    			mSpaceDragLastDiff + ". mSpaceKey.width=" + mSpaceKey.width);
        if (mSpaceKey == null 
                || Math.abs(mSpaceDragLastDiff) < mSpaceKey.width * SPACEBAR_DRAG_THRESHOLD ) {
            return 0; // No change
        }
        return mSpaceDragLastDiff > 0 ? 1 : -1;
    }
    private int getTextSizeFromTheme(int style, int defValue) {
        TypedArray array = mContext.getTheme().obtainStyledAttributes(
                style, new int[] { android.R.attr.textSize });
        int textSize = array.getDimensionPixelSize(array.getResourceId(0, 0), defValue);
        return textSize;
    }
    private void setDefaultBounds(Drawable drawable) {
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }
    class LIMEKey extends Keyboard.Key {
       
    	private boolean mShiftLockEnabled;

    	 // functional normal state (with properties)
        private final int[] KEY_STATE_FUNCTIONAL_NORMAL = {
                android.R.attr.state_single
        };

        // functional pressed state (with properties)
        private final int[] KEY_STATE_FUNCTIONAL_PRESSED = {
                android.R.attr.state_single,
                android.R.attr.state_pressed
        };
        
        private boolean isFunctionalKey() {
            return !sticky && modifier;
        }
        
        @Override
        public int[] getCurrentDrawableState() {
            if (isFunctionalKey()) {
                if (pressed) {
                    return KEY_STATE_FUNCTIONAL_PRESSED;
                } else {
                    return KEY_STATE_FUNCTIONAL_NORMAL;
                }
            }
            return super.getCurrentDrawableState();
        }
        
        public LIMEKey(Resources res, Keyboard.Row parent, int x, int y, XmlResourceParser parser) {
            super(res, parent, x, y, parser);
            if (popupCharacters != null && popupCharacters.length() == 0) {
                // If there is a keyboard with no keys specified in popupCharacters
                popupResId = 0;
            }
        }
        
        @Override
        public void onReleased(boolean inside) {
            if (!mShiftLockEnabled) {
                super.onReleased(inside);
            } else {
                pressed = !pressed;
            }
        }

        void enableShiftLock() {
            mShiftLockEnabled = true;
        }
        /**
         * Overriding this method so that we can reduce the target area for the key that
         * closes the keyboard. 
         */
        @Override
        public boolean isInside(int x, int y) {
//        	 final int code = codes[0];
//             if (code == KEYCODE_SHIFT ||
//                     code == KEYCODE_DELETE) {
//                 y -= height / 10;
//                 if (code == KEYCODE_SHIFT) x += width / 6;
//                 if (code == KEYCODE_DELETE) x -= width / 6;
//             }
//              if (code == KEYCODE_CANCEL) y  -= 10;
              
           
            return	LIMEKeyboard.this.isInside(this, x, y);
            
            //return super.isInside(x,  y);
        }
        boolean isInsideSuper(int x, int y) {
            return super.isInside(x, y);
        }

    }
    private static final int OPACITY_FULLY_OPAQUE = 255;
    /**
     * Animation to be displayed on the spacebar preview popup when switching 
     * IM by swiping the spacebar. It draws the current, previous and
     * next languages and moves them by the delta of touch movement on the spacebar.
     */
    class SlidingSpaceBarDrawable extends Drawable {

        private final int mWidth;
        private final int mHeight;
        private final Drawable mBackground;
        private final TextPaint mTextPaint;
        private final int mMiddleX;
        private final Drawable mLeftDrawable;
        private final Drawable mRightDrawable;
        private final int mThreshold;
        private int mDiff;
        private boolean mHitThreshold;
        private String mCurrentLanguage;
        private String mNextLanguage;
        private String mPrevLanguage;

        public SlidingSpaceBarDrawable(Drawable background, int width, int height) {
            mBackground = background;
            setDefaultBounds(mBackground);
            mWidth = width;
            mHeight = height;
            mTextPaint = new TextPaint();
            mTextPaint.setTextSize(getTextSizeFromTheme(android.R.style.TextAppearance_Medium, 18));
            mTextPaint.setColor(R.color.limekeyboard_transparent);
            mTextPaint.setTextAlign(Align.CENTER);
            mTextPaint.setAlpha(OPACITY_FULLY_OPAQUE);
            mTextPaint.setAntiAlias(true);
            mMiddleX = (mWidth - mBackground.getIntrinsicWidth()) / 2;
            mLeftDrawable =
                    mRes.getDrawable(R.drawable.ic_suggest_strip_scroll_left_arrow);
            mRightDrawable =
                    mRes.getDrawable(R.drawable.ic_suggest_strip_scroll_right_arrow);
            mThreshold = ViewConfiguration.get(mContext).getScaledTouchSlop();
        }

        private void setDiff(int diff) {
            if (diff == Integer.MAX_VALUE) {
                mHitThreshold = false;
                mCurrentLanguage = null;
                return;
            }
            mDiff = diff;
            if (mDiff > mWidth) mDiff = mWidth;
            if (mDiff < -mWidth) mDiff = -mWidth;
            if (Math.abs(mDiff) > mThreshold) mHitThreshold = true;
            invalidateSelf();
        }

       

        @Override
        public void draw(Canvas canvas) {
            canvas.save();
            if (mHitThreshold) {
                Paint paint = mTextPaint;
                final int width = mWidth;
                final int height = mHeight;
                final int diff = mDiff;
                final Drawable lArrow = mLeftDrawable;
                final Drawable rArrow = mRightDrawable;
                canvas.clipRect(0, 0, width, height);
                if (mCurrentLanguage == null) {
                    mCurrentLanguage = LIMEKeyboardSwitcher.getInstance().getCurrentActiveKeyboardShortname();
                    //mNextLanguage = getLanguageName(languageSwitcher.getNextInputLocale());
                    //mPrevLanguage = getLanguageName(languageSwitcher.getPrevInputLocale());
                }
                // Draw language text with shadow
                final float baseline = mHeight * 0.6f - paint.descent();
                paint.setColor(mRes.getColor(R.color.limekeyboard_feedback_language_text));
                canvas.drawText(mCurrentLanguage, width / 2 + diff, baseline, paint);
                canvas.drawText(mNextLanguage, diff - width / 2, baseline, paint);
                canvas.drawText(mPrevLanguage, diff + width + width / 2, baseline, paint);

                setDefaultBounds(lArrow);
                rArrow.setBounds(width - rArrow.getIntrinsicWidth(), 0, width,
                        rArrow.getIntrinsicHeight());
                lArrow.draw(canvas);
                rArrow.draw(canvas);
            }
            if (mBackground != null) {
                canvas.translate(mMiddleX, 0);
                mBackground.draw(canvas);
            }
            canvas.restore();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setAlpha(int alpha) {
            // Ignore
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // Ignore
        }

        @Override
        public int getIntrinsicWidth() {
            return mWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mHeight;
        }
    }

}
