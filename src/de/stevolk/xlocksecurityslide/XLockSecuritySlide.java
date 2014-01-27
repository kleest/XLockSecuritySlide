package de.stevolk.xlocksecurityslide;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XLockSecuritySlide implements IXposedHookLoadPackage {
	public Object mKeyguardSelectorView = null;
	public Unhook lastHook = null;

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		final String packageName = lpparam.packageName;
		final ClassLoader cloader = lpparam.classLoader;
		final Object inst = this;

		// we need to get into the lockscreen, which belongs to system package "android"
		if (!packageName.equals("android"))
			return;
		
		// get enum value for "SecurityMode.None" (static)
		final Class<?> securityModeEnum = XposedHelpers.findClass("com.android.internal.policy.impl.keyguard.KeyguardSecurityModel$SecurityMode", cloader);
		final Object SecNone = XposedHelpers.getStaticObjectField(securityModeEnum, "None");
		
		// showPrimarySecurityScreen --> before: showSecurityScreen(None) --> prevent original (is called anyway..)
		// calling showSecurityScreen with None means, no security measure is set -> sliding
		XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.keyguard.KeyguardHostView", cloader, "showPrimarySecurityScreen", "boolean", new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {

				XposedBridge.log("--showPrimarySecurityScreen(..)");
				XposedBridge.log("--");
				XposedHelpers.callMethod(param.thisObject, "showSecurityScreen", SecNone);
				param.setResult(null);	// prevent original call, it gets called anyway to type in your security measure (somehow..)
				return;
			}						
		});
		
		XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.keyguard.KeyguardHostView", cloader, "showSecurityScreen", securityModeEnum, new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {

				XposedBridge.log("-- showSecurityScreen(..)");
				XposedBridge.log(param.args[0].toString());
				return;
			}						
		});
		
		XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.keyguard.KeyguardHostView", cloader, "showNextSecurityScreenIfPresent", new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {

				XposedBridge.log("-- showNextSecurityScreenIfPresent(..)");
				return;
			}						
		});
		
		XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.keyguard.KeyguardHostView", cloader, "showNextSecurityScreenOrFinish", boolean.class, new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {

				XposedBridge.log("-- showNextSecurityScreenOrFinish(..)");
				XposedBridge.log(param.args[0].toString());
				param.args[0] = true;
				return;
			}
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {

				XposedBridge.log("-- showNextSecurityScreenOrFinish:after(..)");
				XposedBridge.log(param.args[0].toString());
				return;
			}
		});
		
	

/*
	// Possible, but not working fix for second security measure: 
		// CM misses a mCallback.dismiss(false) in their OnTriggerListener that leads to the user answering the security measure two times
		// solve this in this way:
		// An instance of KeyguardSelectorView has to be saved to get access to mOnTriggerListener
		// do this by intercepting constructor method
		// it is save to do this because only one instance of KeyguardSelectorView will be present at any one time
		Constructor<?> KeyguardSelectorViewConstructor = XposedHelpers.findClass("com.android.internal.policy.impl.keyguard.KeyguardSelectorView", cloader).getConstructor(android.content.Context.class, android.util.AttributeSet.class);
		if (KeyguardSelectorViewConstructor != null)
			XposedBridge.hookMethod(KeyguardSelectorViewConstructor, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param)
						throws Throwable {
					XposedBridge.log("-- KeyguardSelectorViewConstructor(..)");
					//((XLockSecuritySlide)inst).mKeyguardSelectorView = param.thisObject;
					
					final Object KeySelectorViewInst = param.thisObject;
					
					XposedBridge.log(KeySelectorViewInst.toString());
					
					// remove last hook
					if (((XLockSecuritySlide)inst).lastHook == null) {
						//((XLockSecuritySlide)inst).lastHook.unhook();
						//((XLockSecuritySlide)inst).lastHook = null;
					

						//XposedHelpers.findAndHookMethod("com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener", cloader, "onTrigger", "android.view.View", "int", new XC_MethodHook() {
						final Class<?> KeyguardSelectorView = XposedHelpers.findClass("com.android.internal.policy.impl.keyguard.KeyguardSelectorView", cloader);
						Field mOnTriggerListener = XposedHelpers.findField(KeyguardSelectorView, "mOnTriggerListener");
						((XLockSecuritySlide)inst).lastHook = XposedHelpers.findAndHookMethod(mOnTriggerListener.get(KeySelectorViewInst).getClass(), "onTrigger", "android.view.View", "int", new XC_MethodHook() {
							@Override
							protected void beforeHookedMethod(MethodHookParam param)
									throws Throwable {
								//((XLockSecuritySlide)inst).mKeyguardSelectorView = param.thisObject;
								XposedBridge.log("-- onTrigger(..)");
								XposedBridge.log(param.thisObject.toString());
								
								Field mCallback = XposedHelpers.findField(KeyguardSelectorView, "mCallback");
								XposedHelpers.callMethod(mCallback.get(KeySelectorViewInst), "dismiss", true); //(KeyguardSelectorView, "mCallback");
								return;
							}
						});
					}
				}
			});
		
		// onFinishInflate calls .setOnTriggerListener(), before-interception
		// onTriggerListener: onTrigger --> before: mCallback.dismiss(false) --> not prevent original
		//XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.keyguard.KeyguardSelectorView", "", parameterTypesAndCallback)
	 */
	}

}
