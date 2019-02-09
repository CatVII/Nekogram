package tw.nekomimi.nekogram;

import android.content.Context;
import android.database.DataSetObserver;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class TabsView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    public static final int[] dialogTypes = new int[]{
            TabsHelper.DialogType.All,
            TabsHelper.DialogType.Users,
            TabsHelper.DialogType.Groups,
            TabsHelper.DialogType.Channels,
            TabsHelper.DialogType.Bots,
            TabsHelper.DialogType.Admin
    };

    private static final int[] tabIcons = {
            R.drawable.menu_chats,
            R.drawable.usersearch,
            R.drawable.menu_newgroup,
            R.drawable.menu_broadcast,
            R.drawable.tab_bot,
            R.drawable.profile_admin
    };

    public enum TabIndex {
        All(0),
        Users(1),
        Groups(2),
        Channels(3),
        Bots(4),
        Admin(5);

        public final int value;

        TabIndex(int value) {
            this.value = value;
        }
    }

    private class Tab {
        public final TabIndex index;
        public final int icon;

        Tab(TabIndex index) {
            this.index = index;
            this.icon = tabIcons[index.value];
        }
    }

    public interface Listener {
        void onPageSelected(int position, int tabIndex);

        void onTabClick();
    }

    private ArrayList<Tab> tabsArray = new ArrayList<>();
    private int[] indexToPosition = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1};
    private TabsPagerTitleStrip tabsPagerTitleStrip;
    private ViewPager pager;
    private int currentTabIndex = TabsConfig.currentTab;
    private Listener listener;
    private boolean saveSelectedTab = true;

    public ViewPager getPager() {
        return pager;
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void setCurrentTabIndex(TabIndex index) {
        this.currentTabIndex = index.value;
    }

    public void setSaveSelectedTab(boolean saveSelectedTab) {
        this.saveSelectedTab = saveSelectedTab;
    }

    public TabsView(Context context) {
        super(context);
        pager = new ViewPager(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);

                return super.onInterceptTouchEvent(ev);
            }

        };
        loadTabs();
        addView(pager, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        LinearLayout tabsContainer = new LinearLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);

                return super.onInterceptTouchEvent(ev);
            }
        };

        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        addView(tabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        tabsPagerTitleStrip = new TabsPagerTitleStrip(context);
        tabsPagerTitleStrip.setViewPager(pager);
        tabsPagerTitleStrip.setIndicatorHeight(AndroidUtilities.dp(3));
        tabsPagerTitleStrip.setDividerColor(0x00000000);
        tabsPagerTitleStrip.setUnderlineHeight(0);
        tabsPagerTitleStrip.setUnderlineColor(0x00000000);
        tabsPagerTitleStrip.setDelegate(new TabsPagerTitleStrip.PlusScrollSlidingTabStripDelegate() {
            @Override
            public void onTabsUpdated() {
                unreadCount();
            }

            @Override
            public void onTabClick() {
                if (listener != null) {
                    listener.onTabClick();
                }
            }
        });
        tabsPagerTitleStrip.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            private boolean loop;

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                currentTabIndex = tabsArray.get(position).index.value;
                saveCurrentTab();
                if (listener != null)
                    listener.onPageSelected(position, currentTabIndex);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                int currentPage = indexToPosition[currentTabIndex];
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    if (loop && pager.getAdapter() != null) {
                        AndroidUtilities.runOnUIThread(() -> pager.setCurrentItem(currentPage == 0 ?
                                pager.getAdapter().getCount() - 1 : 0), 100);
                        loop = false;
                    }
                } else if (state == 1 && pager.getAdapter() != null)
                    loop = !TabsConfig.disableTabsInfiniteScrolling && (currentPage == 0 || currentPage == pager.getAdapter().getCount() - 1);
                else if (state == 2)
                    loop = false;
            }
        });

        tabsContainer.addView(tabsPagerTitleStrip, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f));
        unreadCount();
    }

    private void loadTabs() {
        tabsArray.clear();
        TabIndex[] items = TabIndex.values();
        int position = 0;
        boolean isValidCurrentTabIndex = false;

        for (TabIndex tabIndex : items) {
            if (tabIndex == TabIndex.All && TabsConfig.hideALl)
                continue;
            else if (tabIndex == TabIndex.Users && TabsConfig.hideUsers)
                continue;
            else if (tabIndex == TabIndex.Groups && TabsConfig.hideGroups)
                continue;
            else if (tabIndex == TabIndex.Channels && TabsConfig.hideChannels)
                continue;
            else if (tabIndex == TabIndex.Bots && TabsConfig.hideBots)
                continue;
            else if (tabIndex == TabIndex.Admin && TabsConfig.hideAdmins)
                continue;

            indexToPosition[tabIndex.value] = position;
            tabsArray.add(position, new Tab(tabIndex));

            if (currentTabIndex == tabIndex.value)
                isValidCurrentTabIndex = true;

            position++;
        }

        if (!isValidCurrentTabIndex && tabsArray.size() > 0) {
            currentTabIndex = tabsArray.get(0).index.value;
            saveCurrentTab();
        }

        pager.setAdapter(null);
        pager.setOffscreenPageLimit(tabsArray.size());
        pager.setAdapter(new TabsAdapter());
        post(() -> pager.setCurrentItem(indexToPosition[currentTabIndex]));
    }

    private void saveCurrentTab() {
        if (!saveSelectedTab)
            return;

        TabsConfig.currentTab = currentTabIndex;
        TabsConfig.setCurrentTab(TabsConfig.currentTab);
    }

    public int getVisibleTabsCount() {
        return tabsArray.size();
    }

    public void reloadTabs() {
        loadTabs();
        if (pager.getAdapter() != null)
            pager.getAdapter().notifyDataSetChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.refreshTabsCounters);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.refreshTabsCounters);
    }

    @Override
    public void didReceivedNotification(int id, int accountId, Object... args) {
        if (id == NotificationCenter.refreshTabsCounters) {
            if (tabsArray != null && tabsArray.size() > 1)
                unreadCount();
        }
    }

    private void unreadCount() {
        if (!TabsConfig.hideAdmins)
            unreadCount(TabsHelper.getInstance(UserConfig.selectedAccount).dialogsAdmin,
                    indexToPosition[TabIndex.Admin.value]);

        if (!TabsConfig.hideBots)
            unreadCount(TabsHelper.getInstance(UserConfig.selectedAccount).dialogsBots,
                    indexToPosition[TabIndex.Bots.value]);

        if (!TabsConfig.hideChannels)
            unreadCount(TabsHelper.getInstance(UserConfig.selectedAccount).dialogsChannels,
                    indexToPosition[TabIndex.Channels.value]);

        if (!TabsConfig.hideGroups)
            unreadCount(TabsHelper.getInstance(UserConfig.selectedAccount).dialogsGroups,
                    indexToPosition[TabIndex.Groups.value]);

        if (!TabsConfig.hideUsers)
            unreadCount(TabsHelper.getInstance(UserConfig.selectedAccount).dialogsUsers,
                    indexToPosition[TabIndex.Users.value]);

        if (!TabsConfig.hideALl)
            unreadCount(MessagesController.getInstance(UserConfig.selectedAccount).dialogs,
                    indexToPosition[TabIndex.All.value]);
    }

    private void unreadCount(final ArrayList<TLRPC.TL_dialog> dialogs, int position) {
        if (position == -1)
            return;

        boolean allMuted = true;
        int unreadCount = 0;

        if (dialogs != null && !dialogs.isEmpty()) {
            for (int a = 0; a < dialogs.size(); a++) {
                TLRPC.TL_dialog dialog = dialogs.get(a);
                if (dialog != null && dialog.unread_count > 0) {
                    boolean isMuted = MessagesController.getInstance(UserConfig.selectedAccount).isDialogMuted(dialog.id);
                    if (!isMuted) {
                        int i = dialog.unread_count;

                        if (i > 0) {
                            unreadCount = unreadCount + i;
                            allMuted = false;
                        }
                    }
                }
            }
        }

        tabsPagerTitleStrip.updateCounter(position, unreadCount, allMuted);
    }

    private class TabsAdapter extends PagerAdapter implements TabsPagerTitleStrip.IconTabProvider {
        @Override
        public int getCount() {
            return tabsArray.size();
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if (tabsPagerTitleStrip != null)
                tabsPagerTitleStrip.notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup viewGroup, int position) {
            View view = new View(viewGroup.getContext());
            viewGroup.addView(view);
            return view;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup viewGroup, int position, @NonNull Object object) {
            viewGroup.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
            super.unregisterDataSetObserver(observer);
        }

        @Override
        public int getPageIconResId(int position) {
            return tabsArray.get(position).icon;
        }

    }
}