package com.ls.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ls.myapplication.R;

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
    private boolean ableToPull;//是否可以下拉，只有ListView滚到头才能下拉
    private PullToRefreshListener mListener;//下拉刷新回调接口

    public RefreshableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        header = LayoutInflater.from(context).inflate(R.layout.pull_to_refresh, null, true);
        arrow = (ImageView) header.findViewById(R.id.arrow);
        progressBar = (ProgressBar) header.findViewById(R.id.progress_bar);
        description = (TextView) header.findViewById(R.id.description);
        updateAt = (TextView) header.findViewById(R.id.updated_at);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        refreshUpdatedAtValue();
        setOrientation(VERTICAL);
        addView(header, 0);

    }

    private void refreshUpdatedAtValue() {
        lastUpdateTime = preferences.getLong(UPDATE_AT + mId, -1);
        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - lastUpdateTime;
        long timeIntoFormat;
        String updateAtValue;
        if (lastUpdateTime == -1) {
            updateAtValue = getResources().getString(R.string.not_updated_yet);
        } else if (timePassed < 0) {
            updateAtValue = getResources().getString(R.string.time_error);
        } else if (timePassed < ONE_MINUTE) {
            updateAtValue = getResources().getString(R.string.updated_just_now);
        } else if (timePassed < ONE_HOUR) {
            timeIntoFormat = timePassed / ONE_MINUTE;
            String value = timeIntoFormat + "分钟";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_DAY) {
            timeIntoFormat = timePassed / ONE_HOUR;
            String value = timeIntoFormat + "小时";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_MONTH) {
            timeIntoFormat = timePassed / ONE_DAY;
            String value = timeIntoFormat + "天";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_YEAR) {
            timeIntoFormat = timePassed / ONE_MONTH;
            String value = timeIntoFormat + "个月";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else {
            timeIntoFormat = timePassed / ONE_YEAR;
            String value = timeIntoFormat + "年";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        }
        updateAt.setText(updateAtValue);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !loadOnce) {
            hideHeaderHeight = -header.getHeight();
            headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
            headerLayoutParams.topMargin = hideHeaderHeight;//注意是负数
            listView = (ListView) getChildAt(1);//得到第二个子元素
            listView.setOnTouchListener(this);
            loadOnce = true;
        }
    }

    /**
     * 注意此处是ListView监听状态变化
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        setIsAbleToPull(event);
        if (ableToPull) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDown = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float yMove = event.getRawY();
                    int distance = (int) (yMove - yDown);
                    if (distance < 0 && headerLayoutParams.topMargin <= hideHeaderHeight) {//如果手势向上并且下拉头隐藏的就屏蔽改事件
                        return false;
                    }
                    if (distance < touchSlop) {
                        return false;
                    }
                    if (currentStatue != STATUS_REFRESHING) {
                        if (headerLayoutParams.topMargin > 0) {//下拉头完全显示
                            currentStatue = STATUS_RELEASE_TO_REFRESH;//释放就刷新
                        } else {
                            currentStatue = STATUS_PULL_TO_REFRESH;
                        }
                        headerLayoutParams.topMargin = (distance / 2) + hideHeaderHeight;//设置下拉距离
                        header.setLayoutParams(headerLayoutParams);//通过改变布局参数达到下拉的目的
                    }
                    break;

                case MotionEvent.ACTION_UP:
                default:
                    if (currentStatue == STATUS_RELEASE_TO_REFRESH) {
                        //松手时如果是释放立即刷新状态，调用正在刷新是的任务
                        new RefreshingTask().execute();
                    } else if (currentStatue == STATUS_PULL_TO_REFRESH) {
                        //松手时如果是下拉状态，调用隐藏下拉头的任务
                        new HideHeaderTask().execute();
                    }
                    break;
            }
            //更新下拉头显示信息
            if (currentStatue == STATUS_PULL_TO_REFRESH || currentStatue == STATUS_RELEASE_TO_REFRESH) {
                updateHeaderView();
                listView.setPressed(false);
                listView.setFocusable(false);
                listView.setFocusableInTouchMode(false);
                lastStatus = currentStatue;
                return true;//当前处于下拉、释放刷新状态，返回true屏蔽listview的滚动事件，同时要让ListView失去焦点
            }

        }
        return false;
    }

    /**
     * 更新下拉头的显示状态
     */
    private void updateHeaderView() {
        if (lastStatus != currentStatue) {
            if (currentStatue == STATUS_PULL_TO_REFRESH) {
                description.setText("下拉可以刷新");
                arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatue == STATUS_RELEASE_TO_REFRESH) {
                description.setText("释放立即刷新");
                arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatue == STATUS_REFRESHING) {
                description.setText("正在刷新...");
                progressBar.setVisibility(View.VISIBLE);
                arrow.clearAnimation();
                arrow.setVisibility(View.GONE);
            }
            refreshUpdatedAtValue();
        }
    }

    /**
     * 根据当前状态旋转箭头
     */
    private void rotateArrow() {
        float pivotY = arrow.getHeight() / 2f;
        float pivotX = arrow.getWidth() / 2f;
        float fromDegrees = 0f;
        float toDegrees = 0f;
        if (currentStatue == STATUS_PULL_TO_REFRESH) {
            fromDegrees = 180f;
            toDegrees = 360f;

        } else if (currentStatue == STATUS_RELEASE_TO_REFRESH) {
            fromDegrees = 0f;
            toDegrees = 180f;
        }
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees, pivotX, pivotY);
        animation.setDuration(500);
        animation.setFillAfter(true);//结束后恢复原状
        arrow.startAnimation(animation);

    }

    /**
     * 根据当前ListView的滚动状态来设定{@link #ableToPull}的值,在OnTouch中第一个执行，判断是该滑动还是下拉
     */
    private void setIsAbleToPull(MotionEvent event) {
        View firstChild = listView.getChildAt(0);//当前可见第一个元素
        if (firstChild != null) {
            int firstVisiblePos = listView.getFirstVisiblePosition();//可见第一个元素下表
            if (firstVisiblePos == 0 && firstChild.getTop() == 0) {//可见元素第一个的下标为0代表为ListView中的第一个元素。
                if (!ableToPull) {
                    yDown = event.getRawY();//原始Y坐标
                }
                ableToPull = true;
            } else {
                if (headerLayoutParams.topMargin != hideHeaderHeight) {//下拉头的隐藏
                    headerLayoutParams.topMargin = hideHeaderHeight;
                    header.setLayoutParams(headerLayoutParams);
                    ableToPull = false;
                }
            }
        } else {//listView没有元素应该允许刷新
            ableToPull = true;
        }
    }

    public interface PullToRefreshListener {
        void onRefresh();//刷新时去回调该方法，此方法在子线程中执行不需要另开线程
    }

    /**
     * 正在刷新时执行的任务，回调注册的接口
     */
    class RefreshingTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            int topMargin = headerLayoutParams.topMargin;
            while (true) {
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= 0) {
                    topMargin = 0;
                    break;
                }
                publishProgress(topMargin);//发送更新到UI线程，同时出发{link #onProgressUpdate}方法
                sleep(10);
            }
            currentStatue = STATUS_REFRESHING;
            publishProgress(0);
            if (mListener != null) {//回调接口中的更新操作
                mListener.onRefresh();
            }
            return null;
        }

        /**
         * 该函数由UI线程在publishProgress(Progress...)方法调用完后被调用。一般用于动态地显示一个进度条。
         */
        @Override
        protected void onProgressUpdate(Integer... topMargin) {
            updateHeaderView();
            headerLayoutParams.topMargin = topMargin[0];
            header.setLayoutParams(headerLayoutParams);
            super.onProgressUpdate(topMargin);
        }
    }

    /**
     * 隐藏下拉头的任务
     */
    class HideHeaderTask extends AsyncTask<Void, Integer, Integer> {
        @Override
        protected Integer doInBackground(Void... params) {
            int topMarging = headerLayoutParams.topMargin;
            while (true) {
                topMarging = topMarging + SCROLL_SPEED;
                if (topMarging <= hideHeaderHeight) {
                    topMarging = hideHeaderHeight;
                    break;
                }
                publishProgress(topMarging);
                sleep(10);
            }
            return topMarging;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            headerLayoutParams.topMargin = values[0];
            header.setLayoutParams(headerLayoutParams);

        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            headerLayoutParams.topMargin = integer;
            header.setLayoutParams(headerLayoutParams);
            currentStatue = STATUS_REFRESH_FINISH;
        }
    }

    /**
     * 是当前线程睡眠制定时间
     */
    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 给下拉头注册监听器，完成刷新任务
     */
    public void setOnRefreshListener(PullToRefreshListener pullToRefreshListener, int id) {
        this.mListener = pullToRefreshListener;
        this.mId = id;
    }

    /**
     * 刷新任务执行完成之后调用，结束刷新状态
     */
    public void finishRefreshing() {
        currentStatue = STATUS_REFRESH_FINISH;
        preferences.edit().putLong(UPDATE_AT + mId, System.currentTimeMillis()).commit();
        new HideHeaderTask().execute();
    }
}
