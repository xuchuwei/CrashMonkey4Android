/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.testtype;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceSelection;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JUnit4 test runner that also accommodate {@link IDeviceTest}. Should be specify above JUnit4 Test
 * with the RunWith annotation.
 */
public class DeviceJUnit4ClassRunnerExt extends BlockJUnit4ClassRunner
        implements IDeviceTest,
                IBuildReceiver  {
    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private Map<ITestDevice, IBuildInfo> mDeviceInfos;

    /** Keep track of the list of downloaded files. */
    private List<File> mDownloadedFiles = new ArrayList<>();

    //@Option(name = HostTest.SET_OPTION_NAME, description = HostTest.SET_OPTION_DESC)
    private List<String> mKeyValueOptions = new ArrayList<>();

    public DeviceJUnit4ClassRunnerExt(Class<?> klass) throws InitializationError {
        super(klass);

        String[] args = {"junit4", ""};
        IConfiguration config = null;
        
        try {
	    GlobalConfiguration.createGlobalConfiguration(args);
		//config = ConfigurationFactory.getInstance().createConfigurationFromArgs(args);
		config = new Configuration("name", "getDescription");

	        IDeviceManager manager = DeviceManager.getInstance();        
		IDeviceSelection options = config.getDeviceRequirements();
		
		manager.init();
		//mDevice = manager.allocateDevice(0, options);
		mDevice = manager.allocateDevice(3000);
		  
        } catch (ConfigurationException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch(Throwable e) {
	    e.printStackTrace();
	}
  
	if (mDevice == null) {
	    throw new InitializationError("No device found!");
	}
    }

    /**
     * We override createTest in order to set the device.
     */
    @Override
    protected Object createTest() throws Exception {
        Object testObj = super.createTest();
        if (testObj instanceof IDeviceTest) {
            if (mDevice == null) {
                throw new IllegalArgumentException("Missing device");
            }
            ((IDeviceTest) testObj).setDevice(mDevice);
        }
        if (testObj instanceof IBuildReceiver) {
            if (mBuildInfo == null) {
                throw new IllegalArgumentException("Missing build information");
            }
            ((IBuildReceiver) testObj).setBuild(mBuildInfo);
        }

        // Set options of test object
        //HostTest.setOptionToLoadedObject(testObj, mKeyValueOptions);
        //mDownloadedFiles.addAll(resolveRemoteFileForObject(testObj));
        return testObj;
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        //RunNotifierWrapper wrapper = new RunNotifierWrapper(notifier);
        try {
            super.runChild(method, notifier);
        } finally {
            for (File f : mDownloadedFiles) {
                FileUtil.recursiveDelete(f);
            }
        }
        //if (wrapper.getDeviceNotAvailableException() != null) {
            //throw new CarryDnaeError(wrapper.getDeviceNotAvailableException());
        //}
    }

    @Override
    public void run(RunNotifier notifier) {
        //RunNotifierWrapper wrapper = new RunNotifierWrapper(notifier);
        super.run(notifier);

        //if (wrapper.getDeviceNotAvailableException() != null) {
            //throw new CarryDnaeError(wrapper.getDeviceNotAvailableException());
        //}
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @VisibleForTesting
    OptionSetter createOptionSetter(Object obj) throws ConfigurationException {
        return new OptionSetter(obj);
    }

    /*
    private Set<File> resolveRemoteFileForObject(Object obj) {
        try {
            OptionSetter setter = createOptionSetter(obj);
            return setter.validateRemoteFilePath();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
    */

    /**
     * Implementation of {@link ExternalResource} and {@link TestRule}. This rule allows to log
     * metrics during a test case (inside @Test). It guarantees that the metrics map is cleaned
     * between tests, so the same rule object can be re-used.
     *
     * <pre>Example:
     * &#064;Rule
     * public TestMetrics metrics = new TestMetrics();
     *
     * &#064;Test
     * public void testFoo() {
     *     metrics.addTestMetric("key", "value");
     *     metrics.addTestMetric("key2", "value2");
     * }
     *
     * &#064;Test
     * public void testFoo2() {
     *     metrics.addTestMetric("key3", "value3");
     * }
     * </pre>
     */
    public static class TestMetrics extends ExternalResource {

        Description mDescription;
        private Map<String, String> mMetrics = new HashMap<>();
        //private HashMap<String, Metric> mProtoMetrics = new HashMap<>();

        @Override
        public Statement apply(Statement base, Description description) {
            mDescription = description;
            return super.apply(base, description);
        }

        /**
         * Log a metric entry for the test case. Each key within a test case must be unique
         * otherwise it will override the previous value.
         *
         * @param key The key of the metric.
         * @param value The value associated to the key.
         */
        public void addTestMetric(String key, String value) {
            mMetrics.put(key, value);
        }

        /**
         * Log a metric entry in proto format for the test case. Each key within a test case must be
         * unique otherwise it will override the previous value.
         *
         * @param key The key of the metric.
         * @param metric The value associated to the key.
         */
       // public void addTestMetric(String key, Metric metric) {
           // mProtoMetrics.put(key, metric);
       // }

        @Override
        protected void before() throws Throwable {
            mMetrics = new HashMap<>();
            //mProtoMetrics = new HashMap<>();
        }

        @Override
        protected void after() {
            // we inject a Description with an annotation carrying metrics.
            // We have to go around, since Description cannot be extended and RunNotifier
            // does not give us a lot of flexibility to find our metrics back.
            //mProtoMetrics.putAll(TfMetricProtoUtil.upgradeConvert(mMetrics));
            //mDescription.addChild(
            //        Description.createTestDescription(
            //                "METRICS", "METRICS", new MetricAnnotation(mProtoMetrics)));
        }
    }

    /** Fake annotation meant to carry metrics to the reporters. */
    public static class MetricAnnotation implements Annotation {

        //public HashMap<String, Metric> mMetrics = new HashMap<>();

        //public MetricAnnotation(HashMap<String, Metric> metrics) {
        //    mMetrics.putAll(metrics);
       // }

        @Override
        public Class<? extends Annotation> annotationType() {
            return null;
        }
    }

    /**
     * Implementation of {@link ExternalResource} and {@link TestRule}. This rule allows to log logs
     * during a test case (inside @Test). It guarantees that the log list is cleaned between tests,
     * so the same rule object can be re-used.
     *
     * <pre>Example:
     * &#064;Rule
     * public TestLogData logs = new TestLogData();
     *
     * &#064;Test
     * public void testFoo() {
     *     logs.addTestLog("logcat", LogDataType.LOGCAT, new FileInputStreamSource(logcatFile));
     * }
     *
     * &#064;Test
     * public void testFoo2() {
     *     logs.addTestLog("logcat2", LogDataType.LOGCAT, new FileInputStreamSource(logcatFile2));
     * }
     * </pre>
     */
    public static class TestLogData extends ExternalResource {
        private Description mDescription;
        //private List<LogHolder> mLogs = new ArrayList<>();

        @Override
        public Statement apply(Statement base, Description description) {
            mDescription = description;
            return super.apply(base, description);
        }

        public final void addTestLog(
                String dataName, LogDataType dataType, InputStreamSource dataStream) {
            //mLogs.add(new LogHolder(dataName, dataType, dataStream));
        }

        @Override
        protected void after() {
            // we inject a Description with an annotation carrying metrics.
            // We have to go around, since Description cannot be extended and RunNotifier
            // does not give us a lot of flexibility to find our metrics back.
            //mDescription.addChild(
            //        Description.createTestDescription("LOGS", "LOGS", new LogAnnotation(mLogs)));
        }
    }

    /** Fake annotation meant to carry logs to the reporters. */
    public static class LogAnnotation implements Annotation {

        //public List<LogHolder> mLogs = new ArrayList<>();

        //public LogAnnotation(List<LogHolder> logs) {
            //mLogs.addAll(logs);
        //}

        @Override
        public Class<? extends Annotation> annotationType() {
            return null;
        }
    }
}
