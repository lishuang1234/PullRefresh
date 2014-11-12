package com.ls.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Created by ls on 2014/11/12 0012.
 */
public class RefreshableView extends LinearLayout implements View.OnTouchListener {

    private SharedPreferences preferences;
    /**
     * 下拉状态
     */
    public static final int STATUS_PULL_TO_REFRESH = 0;
    public static final int STATUS_RELEASE_TO_REFRESH = 1;//释放立即刷新
    public static final int STATUS_REFRESHING = 2;//正在刷新
    public static final int STATUS_REFRESH_FINISH = 3;//刷新完成或者是未刷新
    public static final int SCROLL_SPEED = -20;//下拉头部回滚速度，向上为负
    public static final long ONE_MINUTE = 60 * 1000;//一分钟用于判断上次刷新时间
    public static final long ONE_HOUR = 60 * ONE_MINUTE;//一小时
    public static final long ONE_DAY = 24 * ONE_HOUR;//一天
    public static final long ONE_MONTH = 30 * ONE_DAY;//一月
    public static final long ONE_YEAR = 12 * ONE_MONTH;//一年
    public static final String UPDATE_AT = "update_at";//sharePreferres键名
    private View header;//头部
    private ListView listView;//要刷新的ListView
    private ProgressBar progressBar;//刷新时的Progressbar
    private ImageView arrow;//刷新时的指示箭头
    private TextView description;//刷新时显示下拉和释放的文字
    private TextView updateAt;//上次更新时间的描述
    private MarginLayoutParams headerLayoutParams;//下拉头布局参数
    private long lastUpdateTime;//上次更新时间的毫秒值
    private int mId = -1;//防止不同界面的下拉刷新在上次更新时间上互有冲突，id区分
    private int hideHeaderHeight;//下拉头的高度
    private int currentStatue = STATUS_REFRESH_FINISH;//当前处理的状态可选值有STATUS_PULL_TO_REFRESH, STATUS_RELEASE_TO_REFRESH, STATUS_REFRESHING 和 STATUS_REFRESH_FINISHED
    private int lastStatus = currentStatue;//记录上一次的状态避免重复操作
    private float yDown;//手指按下的屏幕纵坐标
    private int touchSlop;//在判定为滚动之前手指可以移动的最大值
    private boolean loadOnce;//是否加载过一次Layout这里OnLayout是需要加载一次
    private boolean  ableToPull;//是否可以下拉，只有ListView滚到头才能下拉
    private PullToRefreshListener mListener;//下拉刷新回调接口
    public RefreshableView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }


    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }
    public interface  PullToRefreshListener{
        void onRefresh();//刷新时去回调该方法，此方法在子线程中执行不需要另开线程
    }

}
