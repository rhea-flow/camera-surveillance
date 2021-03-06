package camera_surveillance;

import com.atul.JavaOpenCV.Imshow;
import com.hazelcast.config.Config;
import com.hazelcast.config.MemberAttributeConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import cv_bridge.CvImage;
import hazelcast_distribution.HazelcastDistributionStrategy;
import org.apache.commons.lang3.tuple.MutablePair;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import org.rhea_core.Stream;
import org.rhea_core.network.Machine;
import org.rhea_core.util.functions.Func2;
import ros_eval.RosEvaluationStrategy;
import ros_eval.RosTopic;
import rx_eval.RxjavaEvaluationStrategy;
import sensor_msgs.Image;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Surveillance {
    static final RosTopic<Image> CAMERA = new RosTopic<>("/camera/rgb/image_color");
    /*static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
    static final Imshow window = new Imshow("Live Feed");*/

    public static void main(String[] args) {

        Config config = new Config();
        MemberAttributeConfig memberConfig = new MemberAttributeConfig();
        memberConfig.setIntAttribute("cores", 4);
        memberConfig.setBooleanAttribute("ros", true);
        memberConfig.setBooleanAttribute("Ros", true);
        memberConfig.setStringAttribute("hostname", "localhost");
        memberConfig.setStringAttribute("ip", "128.1.1.68");
        config.setMemberAttributeConfig(memberConfig);
        HazelcastInstance hazelcast = Hazelcast.newHazelcastInstance(config);

        Stream.configure(new HazelcastDistributionStrategy(
                hazelcast,
                Collections.singletonList(new MyMachine()),
                Arrays.asList(
                    RxjavaEvaluationStrategy::new,
                    () -> new RosEvaluationStrategy(new RxjavaEvaluationStrategy(), "localhost", "myros_client")
                )
        ));

        Stream.from(CAMERA).subscribe(im -> System.out.println("Image!"));

        /*Stream.configure(new HazelcastDistributionStrategy(Arrays.asList(
                RxjavaEvaluationStrategy::new,
                () -> new RosEvaluationStrategy(new RxjavaEvaluationStrategy(), "localhost", "myros_client")
        )));

        Stream<Mat> image = Stream.<Image>from(CAMERA).flatMap(im -> {
            try {
                return Stream.just(CvImage.toCvCopy(im).image);
            } catch (Exception e) {
                return Stream.error(e);
            }
        });

        Stream<Mat> initial = image.take(1).cache().repeat();
        Stream.zip(image, initial, (Func2<Mat, Mat, MutablePair<Mat, Mat>>) MutablePair::new)
                .sample(100, TimeUnit.MILLISECONDS) // backpressure
                .timeout(1, TimeUnit.MINUTES) // stop monitoring when video stream stops (does not produce an image for 1 min)
                .filter(Surveillance::containsHuman) // Only stream images that contain some new object
                .map(MutablePair::getLeft)
                .subscribe(
                        window::showImage,
                        e -> window.Window.setVisible(false),
                        () -> window.Window.setVisible(false)
                );*/
    }

    private static boolean containsHuman(MutablePair<Mat,Mat> pair) {
        Mat m1 = pair.getLeft(), m2 = pair.getRight(), m = new Mat();
        Core.absdiff(m1, m2, m);
        Imgproc.threshold(m, m, 80, 255, Imgproc.THRESH_BINARY);
        Imgproc.erode(m, m, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
        for (int i = 0; i < m.rows(); i++)
            for (int j = 0; j < m.cols(); j++) {
                double[] pixel = m.get(i, j);
                double sum = pixel[0]  + pixel[1] + pixel[2];
                if (sum > 50) return true;
            }
        return false;
    }

    private static class MyMachine implements Machine {
        @Override
        public int cores() {
            return 4;
        }
    }
}
