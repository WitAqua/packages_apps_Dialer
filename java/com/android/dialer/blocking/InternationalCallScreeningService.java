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
import android.telecom.Call;
import android.telecom.CallScreeningService;

import com.android.dialer.common.LogUtil;

/**
 * 着信スクリーニングサービス。
 *
 * <p>設定で「国際電話の着信をブロック」が有効なとき、日本以外の国番号を持つ
 * 国際形式の着信を拒否する。それ以外の着信 (国内番号、番号非通知、設定オフ時) は
 * 通常どおり許可する。判定は {@link InternationalCallBlocking} に委譲する。
 */
public final class InternationalCallScreeningService extends CallScreeningService {

  @Override
  public void onScreenCall(Call.Details callDetails) {
    CallResponse response = new CallResponse.Builder().build();
    try {
      if (shouldBlock(callDetails)) {
        LogUtil.i(
            "InternationalCallScreeningService.onScreenCall",
            "blocking international incoming call");
        response =
            new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                // ブロックした着信は履歴に残し、着信通知は出さない
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build();
      }
    } catch (Exception e) {
      // 判定に失敗した着信はブロックしない (通常の着信を誤って拒否しないため)
      LogUtil.e(
          "InternationalCallScreeningService.onScreenCall", "screening failed, allowing call", e);
      response = new CallResponse.Builder().build();
    }
    respondToCall(callDetails, response);
  }

  private boolean shouldBlock(Call.Details callDetails) {
    if (callDetails.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
      return false;
    }
    if (!InternationalCallBlocking.isEnabled(this)) {
      return false;
    }
    Uri handle = callDetails.getHandle();
    String number = (handle == null) ? null : handle.getSchemeSpecificPart();
    return InternationalCallBlocking.isInternationalNumber(this, number);
  }
}
