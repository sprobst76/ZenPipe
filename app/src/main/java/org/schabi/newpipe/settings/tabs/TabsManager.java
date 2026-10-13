package org.schabi.newpipe.settings.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.List;

public final class TabsManager {
    private final SharedPreferences sharedPreferences;
    private final String savedTabsKey;
    private final Context context;
    private SavedTabsChangeListener savedTabsChangeListener;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private TabsManager(final Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.savedTabsKey = context.getString(R.string.saved_tabs_key);
    }

    public static TabsManager getManager(final Context context) {
        return new TabsManager(context);
    }

    public List<Tab> getTabs() {
        final String savedJson = sharedPreferences.getString(savedTabsKey, null);
        try {
            // ZenPipe: kiosk (trending/live/...) and blank tabs are never shown
            final List<Tab> tabs = new java.util.ArrayList<>(
                    TabsJsonHelper.getTabsFromJson(savedJson));
            tabs.removeIf(t -> t instanceof Tab.KioskTab || t instanceof Tab.DefaultKioskTab
                    || t instanceof Tab.BlankTab || t instanceof Tab.HistoryTab);
            return zenWithPraxis(tabs.isEmpty() ? getDefaultTabs() : tabs);
        } catch (final TabsJsonHelper.InvalidJsonException e) {
            Toast.makeText(context, R.string.saved_tabs_invalid_json, Toast.LENGTH_SHORT).show();
            return zenWithPraxis(getDefaultTabs());
        }
    }

    // ZenPipe: the practice tab is always there (right after the feed), also on old setups
    private static List<Tab> zenWithPraxis(final List<Tab> tabs) {
        final Tab praxis = Tab.Type.PRAXIS.getTab();
        if (tabs.contains(praxis)) {
            return tabs;
        }
        final List<Tab> result = new java.util.ArrayList<>(tabs);
        final int feed = result.indexOf(Tab.Type.FEED.getTab());
        result.add(feed >= 0 ? feed + 1 : result.size(), praxis);
        return result;
    }

    public void saveTabs(final List<Tab> tabList) {
        final String jsonToSave = TabsJsonHelper.getJsonToSave(tabList);
        sharedPreferences.edit().putString(savedTabsKey, jsonToSave).apply();
    }

    public void resetTabs() {
        sharedPreferences.edit().remove(savedTabsKey).apply();
    }

    public List<Tab> getDefaultTabs() {
        return TabsJsonHelper.getDefaultTabs();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listener
    //////////////////////////////////////////////////////////////////////////*/

    public void setSavedTabsListener(final SavedTabsChangeListener listener) {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        savedTabsChangeListener = listener;
        preferenceChangeListener = getPreferenceChangeListener();
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    public void unsetSavedTabsListener() {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        preferenceChangeListener = null;
        savedTabsChangeListener = null;
    }

    private SharedPreferences.OnSharedPreferenceChangeListener getPreferenceChangeListener() {
        return (sp, key) -> {
            if (savedTabsKey.equals(key) && savedTabsChangeListener != null) {
                savedTabsChangeListener.onTabsChanged();
            }
        };
    }

    public interface SavedTabsChangeListener {
        void onTabsChanged();
    }
}
