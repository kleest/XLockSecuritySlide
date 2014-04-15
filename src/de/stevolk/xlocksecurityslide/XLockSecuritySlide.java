/**
 * Copyright 2014 Stevolk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.stevolk.xlocksecurityslide;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XLockSecuritySlide implements IXposedHookLoadPackage {
	public Object mKeyguardSelectorView = null;
	public Unhook lastHook = null;
	
	public static final String SONY_LOCKSCREEN = "com.sonyericsson.lockscreen.uxpnxt";

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		final String packageName = lpparam.packageName;
		final ClassLoader cloader = lpparam.classLoader;
		
		String keyGuardPackage = "";
		
		// catch sony customization
		if (lpparam.packageName.equals(SONY_LOCKSCREEN))
			fixSonyLockscreen(lpparam);

//		XposedBridge.log("loadPackage: "+packageName);
		// on Kitkat package name has changed, so differ betwenn KK and JB
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {	// NOT KK -> JB
			// we need to get into the lockscreen, which belongs to system package "android"
			if (!packageName.equals("android"))
				return;
			else
				keyGuardPackage = "com.android.internal.policy.impl.keyguard.";
		} else	// IF KK -> NOT JB
			if (!packageName.equals("com.android.keyguard"))
				return;
			else
				keyGuardPackage = "com.android.keyguard.";
		
		// get enum value for "SecurityMode.None" (static)
		final Class<?> securityModeEnum = XposedHelpers.findClass(keyGuardPackage+"KeyguardSecurityModel$SecurityMode", cloader);
		final Object SecNone = XposedHelpers.getStaticObjectField(securityModeEnum, "None");
		
		// showPrimarySecurityScreen --> before: showSecurityScreen(None) --> prevent original (is called anyway..)
		// calling showSecurityScreen with None means, no security measure is set -> sliding
		XposedHelpers.findAndHookMethod(keyGuardPackage+"KeyguardHostView", cloader, "showPrimarySecurityScreen", "boolean", new XC_MethodHook() {

			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {

//				XposedBridge.log("--showPrimarySecurityScreen(..)");
//				XposedBridge.log("--");
				param.setResult(null);	// prevent original call, it gets called anyway to type in your security measure (somehow..)
				XposedHelpers.callMethod(param.thisObject, "showSecurityScreen", SecNone);				
			}						
		});
		
		final Class<?> KeyguardHostView = XposedHelpers.findClass(keyGuardPackage+"KeyguardHostView", cloader);

		// after lockscreen has been dismissed (sliding happened), showNextSecurityScreenOrFinish is called with boolean argument "unlocked"
		XposedHelpers.findAndHookMethod(KeyguardHostView, "showNextSecurityScreenOrFinish", boolean.class, new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {


//				XposedBridge.log("-- showNextSecurityScreenOrFinish(..) -> "+param.args[0].toString());

				// Show next security screen only if device hasn't been unlocked yet (-> "false")
				// if so, request security mode screen (PIN, pattern etc. AND prevent original call)
				if (param.args[0].equals(false)) {
					// query security mode
					Object mSecurityModel = XposedHelpers.getObjectField(param.thisObject, "mSecurityModel");
					Object secMode = XposedHelpers.callMethod(mSecurityModel, "getSecurityMode");
					
					// if security mode is different from None (avoids endless loop of "None" lockscreens)
					if (!secMode.equals(SecNone)) {
						XposedHelpers.callMethod(param.thisObject, "showSecurityScreen", secMode);
						param.setResult(null);
					}
				}
			}
		});
	}

	private void fixSonyLockscreen(LoadPackageParam lpparam) {
		XposedHelpers.findAndHookMethod("com.sonymobile.lockscreen.xperia.widget.blindslayout.BlindsRelativeLayout", lpparam.classLoader, "onExitTransitionFinished", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				XposedHelpers.setBooleanField(param.thisObject, "mSkipDraw", false);
				XposedHelpers.setBooleanField(param.thisObject, "mDrawingBlinds", false);
			}
		});
		XposedHelpers.findAndHookMethod("com.sonymobile.lockscreen.xperia.FadeAllUnlockTransitionStrategy", lpparam.classLoader, "startUnlockTransition", new XC_MethodHook() {
	        @Override
	        protected void beforeHookedMethod(MethodHookParam param) throws Throwable
	        {
	          param.setResult(null);
	        }
		});
	}
}
