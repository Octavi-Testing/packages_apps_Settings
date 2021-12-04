/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.homepage;

import static android.provider.Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY;
import static android.provider.Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY;
import static android.provider.Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.FeatureFlagUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.window.embedding.SplitRule;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsApplication;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.core.CategoryMixin;
import com.android.settings.core.FeatureFlags;
import com.android.settings.homepage.contextualcards.ContextualCardsFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.Utils;
import com.android.settingslib.core.lifecycle.HideNonSystemOverlayMixin;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import java.util.ArrayList;

import java.net.URISyntaxException;
import java.util.Set;

/** Settings homepage activity */
public class SettingsHomepageActivity extends FragmentActivity implements
        CategoryMixin.CategoryHandler {

    private static final String TAG = "SettingsHomepageActivity";

    // Additional extra of Settings#ACTION_SETTINGS_LARGE_SCREEN_DEEP_LINK.
    // Put true value to the intent when startActivity for a deep link intent from this Activity.
    public static final String EXTRA_IS_FROM_SETTINGS_HOMEPAGE = "is_from_settings_homepage";

    // Additional extra of Settings#ACTION_SETTINGS_LARGE_SCREEN_DEEP_LINK.
    // Set & get Uri of the Intent separately to prevent failure of Intent#ParseUri.
    public static final String EXTRA_SETTINGS_LARGE_SCREEN_DEEP_LINK_INTENT_DATA =
            "settings_large_screen_deep_link_intent_data";

    static final int DEFAULT_HIGHLIGHT_MENU_KEY = R.string.menu_key_network;
    private static final long HOMEPAGE_LOADING_TIMEOUT_MS = 300;

    private TopLevelSettings mMainFragment;
    private View mHomepageView;
    private View mTwoPaneSuggestionView;
    private CategoryMixin mCategoryMixin;
    CollapsingToolbarLayout collapsing_toolbar;
    private Set<HomepageLoadedListener> mLoadedListeners;
    private boolean mIsEmbeddingActivityEnabled;
    private boolean mIsTwoPaneLastTime;

    static ArrayList<String> text=new ArrayList<>();
    static {
        text.add("Thanks, for choosing Octavi!");
	text.add("I wonder how many rejections you had");
	text.add("Always remember that you're unique");
        text.add("Constipated people don’t give a crap");
        text.add("Unicorns ARE real, we call them rhinos");
        text.add("If there is a *WILL*, there are 500 relatives");
        text.add("Those who throw dirt only lose ground");
        text.add("I’d like to help you out");
        text.add("Age is a question of mind over matter");
        text.add("I’m an excellent housekeeper");
        text.add("Change is good, but dollars are better");
        text.add("If you cannot convince them, confuse them");
        text.add("This sentence is a lie");
        text.add("Make everyday a little less ordinary");
        text.add("Stupidity is not a crime so you are free to go.");
        text.add("I'm not insulting you. I'm describing you");
        text.add("You're so fake, Barbie is jealous");
        text.add("Believe you can, and you are halfway there");
	text.add("Whatever you are, be a good one");
    }

    /** A listener receiving homepage loaded events. */
    public interface HomepageLoadedListener {
        /** Called when the homepage is loaded. */
        void onHomepageLoaded();
    }

    private interface FragmentBuilder<T extends Fragment>  {
        T build();
    }

    /**
     * Try to add a {@link HomepageLoadedListener}. If homepage is already loaded, the listener
     * will not be notified.
     *
     * @return Whether the listener is added.
     */
    public boolean addHomepageLoadedListener(HomepageLoadedListener listener) {
        if (mHomepageView == null) {
            return false;
        } else {
            if (!mLoadedListeners.contains(listener)) {
                mLoadedListeners.add(listener);
            }
            return true;
        }
    }

    /**
     * Shows the homepage and shows/hides the suggestion together. Only allows to be executed once
     * to avoid the flicker caused by the suggestion suddenly appearing/disappearing.
     */
    public void showHomepageWithSuggestion(boolean showSuggestion) {
        mTwoPaneSuggestionView.setVisibility(showSuggestion ? View.VISIBLE : View.GONE);

        mLoadedListeners.forEach(listener -> listener.onHomepageLoaded());
        mLoadedListeners.clear();
    }

    /** Returns the main content fragment */
    public TopLevelSettings getMainFragment() {
        return mMainFragment;
    }

    @Override
    public CategoryMixin getCategoryMixin() {
        return mCategoryMixin;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_homepage_container);
        mIsEmbeddingActivityEnabled = ActivityEmbeddingUtils.isEmbeddingActivityEnabled(this);
        mIsTwoPaneLastTime = ActivityEmbeddingUtils.isTwoPaneResolution(this);

        updateHomepageAppBar();
        updateHomepageBackground();
        mLoadedListeners = new ArraySet<>();

        initSearchBarView();

        final View root = findViewById(R.id.settings_homepage_container);
	LinearLayout commonCon = root.findViewById(R.id.common_con);
        final Toolbar toolbar = root.findViewById(R.id.search_action_bar);
	collapsing_toolbar =  root.findViewById(R.id.collapsing_toolbar);

        TextView greeter = root.findViewById(R.id.greeter);
	greeter.setText(text.get(randomNum(0, text.size()-1)));

        FeatureFactory.getFactory(this).getSearchFeatureProvider()
                .initSearchToolbar(this /* activity */, toolbar, SettingsEnums.SETTINGS_HOMEPAGE);

	AppBarLayout appBarLayout = root.findViewById(R.id.appbar);
        appBarLayout.addOnOffsetChangedListener((appBarLayout1, i) -> {

	    float abs = ((float) Math.abs(i)) / ((float) appBarLayout1.getTotalScrollRange());
            float f2 = 1.0f - abs;
            //greeter text
            if (f2 == 1.0)
                ObjectAnimator.ofFloat(greeter, View.ALPHA, 1f).setDuration(500).start();
            else
                greeter.setAlpha(0f);

        });

   getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));
	collapsing_toolbar.setTitle("Settings");
        mCategoryMixin = new CategoryMixin(this);
        getLifecycle().addObserver(mCategoryMixin);

        final String highlightMenuKey = getHighlightMenuKey();
            final boolean scrollNeeded = mIsEmbeddingActivityEnabled
                    && !TextUtils.equals(getString(DEFAULT_HIGHLIGHT_MENU_KEY), highlightMenuKey);
        }
        mMainFragment = showFragment(() -> {
            final TopLevelSettings fragment = new TopLevelSettings();
            fragment.getArguments().putString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY,
                    highlightMenuKey);
            return fragment;
        }, R.id.main_content);

        ((FrameLayout) findViewById(R.id.main_content))
                .getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        // Launch the intent from deep link for large screen devices.
        launchDeepLinkIntentToRight();
    }

    @Override
    protected void onStart() {
        ((SettingsApplication) getApplication()).setHomeActivity(this);
        super.onStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // When it's large screen 2-pane and Settings app is in the background, receiving an Intent
        // will not recreate this activity. Update the intent for this case.
        setIntent(intent);
        reloadHighlightMenuKey();
        if (isFinishing()) {
            return;
        }
        // Launch the intent from deep link for large screen devices.
        launchDeepLinkIntentToRight();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final boolean isTwoPane = ActivityEmbeddingUtils.isTwoPaneResolution(this);
        if (mIsTwoPaneLastTime != isTwoPane) {
            mIsTwoPaneLastTime = isTwoPane;
            updateHomepageAppBar();
            updateHomepageBackground();
        }
    }

    private void initSearchBarView() {
        if (mIsEmbeddingActivityEnabled) {
            final Toolbar toolbarTwoPaneVersion = findViewById(R.id.search_action_bar_two_pane);
            FeatureFactory.getFactory(this).getSearchFeatureProvider()
                    .initSearchToolbar(this /* activity */, toolbarTwoPaneVersion,
                            SettingsEnums.SETTINGS_HOMEPAGE);
        }
    }

    private void initAvatarView() {
        final ImageView avatarTwoPaneView = findViewById(R.id.account_avatar_two_pane_version);
            if (mIsEmbeddingActivityEnabled) {
                avatarTwoPaneView.setVisibility(View.VISIBLE);
                getLifecycle().addObserver(new AvatarViewMixin(this, avatarTwoPaneView));
        }
    }

    private void updateHomepageBackground() {
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        final Window window = getWindow();
        final int color = ActivityEmbeddingUtils.isTwoPaneResolution(this)
                ? Utils.getColorAttrDefaultColor(this, com.android.internal.R.attr.colorSurface)
                : Utils.getColorAttrDefaultColor(this, android.R.attr.colorBackground);

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // Update status bar color
        window.setStatusBarColor(color);
        // Update content background.
        findViewById(R.id.settings_homepage_container).setBackgroundColor(color);
    }

    private void showSuggestionFragment(boolean scrollNeeded) {
        mTwoPaneSuggestionView = findViewById(R.id.two_pane_suggestion_content);
         final FragmentBuilder<?> fragmentBuilder = () -> {
             return null;
        };
        showFragment(fragmentBuilder, R.id.suggestion_content);
        if (mIsEmbeddingActivityEnabled) {
            showFragment(fragmentBuilder, R.id.two_pane_suggestion_content);
        }
    }

    private <T extends Fragment> T showFragment(FragmentBuilder<T> fragmentBuilder, int id) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        T showFragment = (T) fragmentManager.findFragmentById(id);

        if (showFragment == null) {
            showFragment = fragmentBuilder.build();
            fragmentTransaction.add(id, showFragment);
        } else {
            fragmentTransaction.show(showFragment);
        }
        fragmentTransaction.commit();
        return showFragment;
    }

    private void launchDeepLinkIntentToRight() {
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        final Intent intent = getIntent();
        if (intent == null || !TextUtils.equals(intent.getAction(),
                ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY)) {
            return;
        }

        if (!(this instanceof DeepLinkHomepageActivity
                || this instanceof SliceDeepLinkHomepageActivity)) {
            Log.e(TAG, "Not a deep link component");
            finish();
            return;
        }

        final String intentUriString = intent.getStringExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI);
        if (TextUtils.isEmpty(intentUriString)) {
            Log.e(TAG, "No EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI to deep link");
            finish();
            return;
        }

        final Intent targetIntent;
        try {
            targetIntent = Intent.parseUri(intentUriString, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Failed to parse deep link intent: " + e);
            finish();
            return;
        }

        final ComponentName targetComponentName = targetIntent.resolveActivity(getPackageManager());
        if (targetComponentName == null) {
            Log.e(TAG, "No valid target for the deep link intent: " + targetIntent);
            finish();
            return;
        }
        targetIntent.setComponent(targetComponentName);

        // To prevent launchDeepLinkIntentToRight again for configuration change.
        intent.setAction(null);

        targetIntent.setFlags(targetIntent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Sender of intent may want to send intent extra data to the destination of targetIntent.
        targetIntent.replaceExtras(intent);

        targetIntent.putExtra(EXTRA_IS_FROM_SETTINGS_HOMEPAGE, true);
        targetIntent.putExtra(SettingsActivity.EXTRA_IS_FROM_SLICE, false);

        targetIntent.setData(intent.getParcelableExtra(
                SettingsHomepageActivity.EXTRA_SETTINGS_LARGE_SCREEN_DEEP_LINK_INTENT_DATA));

        // Set 2-pane pair rule for the deep link page.
        ActivityEmbeddingRulesController.registerTwoPanePairRule(this,
                new ComponentName(getApplicationContext(), getClass()),
                targetComponentName,
                targetIntent.getAction(),
                SplitRule.FINISH_ALWAYS,
                SplitRule.FINISH_ALWAYS,
                true /* clearTop */);
        ActivityEmbeddingRulesController.registerTwoPanePairRule(this,
                new ComponentName(getApplicationContext(), Settings.class),
                targetComponentName,
                targetIntent.getAction(),
                SplitRule.FINISH_ALWAYS,
                SplitRule.FINISH_ALWAYS,
                true /* clearTop */);
        startActivity(targetIntent);
    }

    private String getHighlightMenuKey() {
        final Intent intent = getIntent();
        if (intent != null && TextUtils.equals(intent.getAction(),
                ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY)) {
            final String menuKey = intent.getStringExtra(
                    EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY);
            if (!TextUtils.isEmpty(menuKey)) {
                return menuKey;
            }
        }
        return getString(DEFAULT_HIGHLIGHT_MENU_KEY);
    }

    private void reloadHighlightMenuKey() {
        mMainFragment.getArguments().putString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY,
                getHighlightMenuKey());
        mMainFragment.reloadHighlightMenuKey();
    }

    private void initHomepageContainer() {
        final View view = findViewById(R.id.homepage_container);
        // Prevent inner RecyclerView gets focus and invokes scrolling.
        view.setFocusableInTouchMode(true);
        view.requestFocus();
    }

    private void updateHomepageAppBar() {
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }
        if (ActivityEmbeddingUtils.isTwoPaneResolution(this)) {
            findViewById(R.id.homepage_app_bar_regular_phone_view).setVisibility(View.GONE);
            findViewById(R.id.homepage_app_bar_two_pane_view).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.homepage_app_bar_regular_phone_view).setVisibility(View.VISIBLE);
            findViewById(R.id.homepage_app_bar_two_pane_view).setVisibility(View.GONE);
        }
    }

    private int getSearchBoxHeight() {
        final int searchBarHeight = getResources().getDimensionPixelSize(R.dimen.search_bar_height);
        final int searchBarMargin = getResources().getDimensionPixelSize(R.dimen.search_bar_margin);
        return searchBarHeight + searchBarMargin * 2;
    }

    private int randomNum(int min , int max) {
	int r = (max - min) + 1;
	return (int)(Math.random() * r) + min;
    }
}
