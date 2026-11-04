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

import android.net.Uri;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;

import com.android.dialer.common.LogUtil;

/**
 * 発信リダイレクションサービス。
 *
 * <p>設定で「国際電話の発信を拒否」が有効なとき、現在いる国以外の
 * 国番号を持つ国際形式への発信を取り消す。それ以外の発信 (国内番号、国内形式、
 * 設定オフ時) はそのまま発信する。判定は {@link InternationalCallBlocking} に委譲する。
 * 緊急通報は Telecom 側で本サービスの対象外となる。
 */
public final class InternationalCallRedirectionService extends CallRedirectionService {

  @Override
  public void onPlaceCall(
      Uri handle, PhoneAccountHandle initialPhoneAccount, boolean allowInteractiveResponse) {
    try {
      if (shouldBlock(handle)) {
        LogUtil.i(
            "InternationalCallRedirectionService.onPlaceCall",
            "blocking international outgoing call");
        cancelCall();
        return;
      }
    } catch (Exception e) {
      // 判定に失敗した発信はブロックしない (通常の発信を誤って妨げないため)
      LogUtil.e(
          "InternationalCallRedirectionService.onPlaceCall", "redirection failed, placing call", e);
    }
    placeCallUnmodified();
  }

  private boolean shouldBlock(Uri handle) {
    if (!InternationalCallBlocking.isOutgoingEnabled(this)) {
      return false;
    }
    String number = (handle == null) ? null : handle.getSchemeSpecificPart();
    return InternationalCallBlocking.isInternationalNumber(this, number);
  }
}
