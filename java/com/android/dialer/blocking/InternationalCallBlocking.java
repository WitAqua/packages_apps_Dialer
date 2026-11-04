/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.dialer.blocking;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Locale;

/**
 * 国際電話の着信ブロック機能の設定キーと判定ロジック。
 *
 * <p>設定画面 (DialerSettingsActivity) のスイッチがデフォルト SharedPreferences に
 * 保存し、着信スクリーニングサービス (InternationalCallScreeningService) が
 * 着信のたびにここを参照する。
 *
 * <p>「国内」の基準は固定せず、端末が現在いる国をそのつど調べて使う
 * (海外旅行中は渡航先が基準になる)。
 */
public final class InternationalCallBlocking {

  /** 設定スイッチのキー (デフォルト SharedPreferences に boolean で保存される) */
  public static final String PREF_KEY_BLOCK_INTERNATIONAL_CALLS =
      "deny_incoming_international_calls";

  /** 発信ブロック設定スイッチのキー (デフォルト SharedPreferences に boolean で保存される) */
  public static final String PREF_KEY_DENY_OUTGOING_INTERNATIONAL_CALLS =
      "deny_outgoing_international_calls";

  /** 現在いる国がどうしても分からない場合の最終フォールバック */
  private static final String FALLBACK_REGION = "JP";

  private InternationalCallBlocking() {}

  /** 国際電話ブロックが有効かどうか */
  public static boolean isEnabled(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(PREF_KEY_BLOCK_INTERNATIONAL_CALLS, false);
  }

  /** 国際電話の発信ブロックが有効かどうか */
  public static boolean isOutgoingEnabled(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(PREF_KEY_DENY_OUTGOING_INTERNATIONAL_CALLS, false);
  }

  /**
   * 端末が現在いる国 (ISO 3166-1 alpha-2、大文字) を返す。
   *
   * <p>優先順位: 現在接続中のネットワークの国 (物理的にいる国) → SIM の国 →
   * 端末ロケールの国 → {@link #FALLBACK_REGION}。
   */
  public static String getCurrentRegion(Context context) {
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    if (telephonyManager != null) {
      String networkCountry = telephonyManager.getNetworkCountryIso();
      if (!TextUtils.isEmpty(networkCountry)) {
        return networkCountry.toUpperCase(Locale.US);
      }
      String simCountry = telephonyManager.getSimCountryIso();
      if (!TextUtils.isEmpty(simCountry)) {
        return simCountry.toUpperCase(Locale.US);
      }
    }
    String localeCountry = Locale.getDefault().getCountry();
    if (!TextUtils.isEmpty(localeCountry)) {
      return localeCountry.toUpperCase(Locale.US);
    }
    return FALLBACK_REGION;
  }

  /**
   * 番号が「現在いる国以外の国番号を持つ国際形式の番号」かどうかを判定する。
   *
   * <p>国際形式 (+ で始まる、または国際発信プレフィックス 00 で始まる) であり、かつ
   * 国番号が現在いる国のものと異なるものを国際電話とみなす。国内形式の番号、
   * 番号非通知 (null)、解釈できない番号はすべて国際電話とはみなさない (ブロックしない)。
   */
  public static boolean isInternationalNumber(Context context, String number) {
    if (number == null) {
      return false;
    }
    String trimmed = number.trim();
    if (!trimmed.startsWith("+") && !trimmed.startsWith("00")) {
      // 国内形式の番号は国際電話ではない
      return false;
    }
    String homeRegion = getCurrentRegion(context);
    try {
      PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
      Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(trimmed, homeRegion);
      return parsed.getCountryCode() != phoneNumberUtil.getCountryCodeForRegion(homeRegion);
    } catch (NumberParseException e) {
      // 解釈できない番号はブロックしない
      return false;
    }
  }
}
