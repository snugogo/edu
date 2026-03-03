package com.school.ai.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 声波纹自定义视图 - 未来感可视化效果
 * 显示音量大小的动态波形
 */
public class WaveformView extends View {

    private Paint paint;
    private float[] bars;
    private Random random;
    private boolean isListening = false;
    private boolean isSpeaking = false;

    private static final int BAR_COUNT = 20;
    private static final int MIN_BAR_HEIGHT = 10;
    private static final int ANIMATION_DELAY = 50;

    // 颜色定义
    private static final int COLOR_LISTENING = Color.parseColor("#00FFFF"); // 青色 - 聆听
    private static final int COLOR_SPEAKING = Color.parseColor("#00FF00"); // 绿色 - AI说话
    private static final int COLOR_IDLE = Color.parseColor("#666666");    // 灰色 - 待机

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStrokeWidth(10f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(COLOR_IDLE);
        
        random = new Random();
        bars = new float[BAR_COUNT];
        resetBars();
    }

    /**
     * 设置聆听状态（用户说话时）
     */
    public void setListening(boolean listening) {
        isListening = listening;
        if (listening) {
            paint.setColor(COLOR_LISTENING);
        } else if (!isSpeaking) {
            paint.setColor(COLOR_IDLE);
            resetBars();
        }
        invalidate();
    }

    /**
     * 设置说话状态（AI回答时）
     */
    public void setSpeaking(boolean speaking) {
        isSpeaking = speaking;
        if (speaking) {
            paint.setColor(COLOR_SPEAKING);
        } else if (!isListening) {
            paint.setColor(COLOR_IDLE);
            resetBars();
        }
        invalidate();
    }

    private void resetBars() {
        for (int i = 0; i < bars.length; i++) {
            bars[i] = MIN_BAR_HEIGHT;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bars == null || bars.length == 0) return;

        int width = getWidth();
        int height = getHeight();
        int gap = width / (bars.length + 1);

        for (int i = 0; i < bars.length; i++) {
            // 如果在活跃状态，随机生成高度模拟声波
            if (isListening || isSpeaking) {
                bars[i] = MIN_BAR_HEIGHT + random.nextInt(height / 2);
            }

            float x = gap * (i + 1);
            float startY = (height - bars[i]) / 2;
            float endY = (height + bars[i]) / 2;

            // 绘制渐变效果
            paint.setAlpha(128 + (i * 6)); // 渐变透明度
            canvas.drawLine(x, startY, x, endY, paint);
        }

        // 继续动画
        if (isListening || isSpeaking) {
            postInvalidateDelayed(ANIMATION_DELAY);
        }
    }
}
