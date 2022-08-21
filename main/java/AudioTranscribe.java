// Imports the Google Cloud client library

import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationTimedPollAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.TimedRetryAlgorithm;
import com.google.cloud.speech.v1.*;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.threeten.bp.Duration;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;


public class AudioTranscribe {
    public static void main(String... args) throws Exception {
        String folderName;
        StringBuilder builder = new StringBuilder();
        String mp3Name;
        int d = 0;
        String line = null;

        File dump = new File("./out");
        if (!dump.exists()){
            dump.mkdirs();
        }

        //Todo: GUI
        // Read from cmd
        System.out.print("Enter URL \n");

        BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(System.in)
        );

        String clipUrl = bufferedReader.readLine();

        // Launch yt-dlp and save mp3
        ProcessBuilder processBuilder = new ProcessBuilder();

        processBuilder.command (
                "yt-dlp",
                "-f", "mp4",
                "--output", "D:\\ws\\learnCloud\\%(title)s_%(id)s.mp3",
                clipUrl
        );

        Process yt_DLP = processBuilder.start();

        // Output status of yt-dlp
        BufferedReader reader = new BufferedReader(new InputStreamReader(yt_DLP.getInputStream()));
        while ( (line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
            String result = builder.toString();
            System.out.print(result);
        }

        yt_DLP.waitFor();
        File newMP3 = new File(String.valueOf(getNewMp3(System.getProperty("user.dir"))));
        mp3Name = FilenameUtils.removeExtension(newMP3.getName());
        System.out.print(newMP3);
        folderName = FilenameUtils.removeExtension(String.valueOf(newMP3));
        File newFolder = new File(folderName);
        if (!newFolder.exists()) {
            newFolder.mkdirs();;
        }

        File source = getNewMp3(System.getProperty("user.dir"));
        File dest = new File(newFolder.getAbsolutePath());
        try {
            FileUtils.copyFileToDirectory(source, dest);
        } catch (IOException e) {
            e.printStackTrace();
        }

        getNewMp3(System.getProperty("user.dir")).delete();

        //Todo: Find better number system here
        int picNum = 1;

        //Todo: Add additional folder per file name

        // Instantiates a client
        String mp3FileString = String.valueOf(getNewMp3(String.valueOf(dest)));
        String mp3FileName = FilenameUtils.removeExtension(String.valueOf(getNewMp3(String.valueOf(dest))));
        String wavFileName = mp3FileName + ".wav";;

        ProcessBuilder mp3ToWav = new ProcessBuilder();
        mp3ToWav.command(
                "ffmpeg",
                "-i", mp3FileString, wavFileName
        );
        System.out.print("\n AAAAa");
        Process mp32Wav = mp3ToWav.start();
        mp32Wav.waitFor();

        // Upload Here
        UploadObject.uploadObject("learngcloud-346222", "yt_transcribe_in", mp3Name, wavFileName);

        // Configure polling algorithm
        SpeechSettings.Builder speechSettings = SpeechSettings.newBuilder();
        TimedRetryAlgorithm timedRetryAlgorithm =
                OperationTimedPollAlgorithm.create(
                        RetrySettings.newBuilder()
                                .setInitialRetryDelay(Duration.ofMillis(500L))
                                .setRetryDelayMultiplier(1.5)
                                .setMaxRetryDelay(Duration.ofMillis(5000L))
                                .setInitialRpcTimeout(Duration.ZERO) // ignored
                                .setRpcTimeoutMultiplier(1.0) // ignored
                                .setMaxRpcTimeout(Duration.ZERO) // ignored
                                .setTotalTimeout(Duration.ofHours(24L)) // set polling timeout to 24 hours
                                .build());
        speechSettings.longRunningRecognizeOperationSettings().setPollingAlgorithm(timedRetryAlgorithm);

        try (SpeechClient speech = SpeechClient.create(speechSettings.build())) {
            // Builds the sync recognize request
            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setEnableAutomaticPunctuation(true)
                            .setEncoding(AudioEncoding.LINEAR16)
                            .setAudioChannelCount(2)
                            .setLanguageCode("en-US")
                            .setModel("video")
                            .setEnableWordTimeOffsets(true)
                            .build();

            RecognitionAudio audio =
                    RecognitionAudio.newBuilder().setUri("gs://yt_transcribe_in/" + mp3Name).build();

            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
                    speech.longRunningRecognizeAsync(config, audio);

            while (!response.isDone()){
                System.out.print("Waiting for response...\n");
                Thread.sleep(1000);
            }

            List<SpeechRecognitionResult> results = response.get().getResultsList();

            for (SpeechRecognitionResult result : results) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                Long timeStart = alternative.getWords(0).getStartTime().getSeconds();
                Long timeEnd = alternative.getWords(alternative.getWordsCount()-1).getEndTime().getSeconds();

                System.out.printf(
                        "%s - %s\n" +
                                "Transcript : %s\n",
                        timeStart,
                        timeEnd,
                        alternative.getTranscript()
                );

                makeJPG(alternative, picNum, dest);
                picNum ++;
            }

            // Delete cloud object
            DeleteObject.deleteObject("learngcloud-346222", "yt_transcribe_in", mp3Name);
            String dupDest = dest + String.valueOf(d);
            FileUtils.copyDirectory(new File(dupDest) , dump);
            d++;
        } catch (Exception e ){
            System.out.print(e);
        }
    }

    public static File getNewMp3(String directoryFilePath) {
        File directory = new File(directoryFilePath);
        File[] files = directory.listFiles(File::isFile);
        File chosenFile = null;

        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".mp3")){
                    chosenFile =file;
                }
            }
        }
        return chosenFile;
    }

    public static void makeJPG(SpeechRecognitionAlternative alternative, int picNum, File dest) throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        Font font = new Font("Arial", Font.PLAIN, 48);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        // ToDo: Truncate the String to standard size
        int width = fm.stringWidth(alternative.getTranscript());
        int height = fm.getHeight();
        g2d.dispose();

        // Makes images with the results
        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setFont(font);
        fm = g2d.getFontMetrics();
        g2d.setColor(Color.white);
        g2d.drawString(alternative.getTranscript(), 0, fm.getAscent());
        g2d.dispose();
        ImageIO.write(img, "png", new File(dest + "/" + String.valueOf(picNum) + ".png"));
    }

    public static String trimLongSentence (SpeechRecognitionAlternative alternative){
        String trimmedString;
        trimmedString = alternative.getTranscript().replaceAll("((?:[^\\s]*\\s){9}[^\\s]*)\\s", "$1\n");
        return trimmedString;
    }
}