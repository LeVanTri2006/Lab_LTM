package com.vku.chat.client;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

record VoiceNote(byte[] wav, int seconds) {
    static final int BYTES_PER_SECOND = 32_000;
    static final AudioFormat FORMAT = new AudioFormat(16_000f, 16, 1, true, false);

    static VoiceNote fromPcm(byte[] pcm) throws IOException {
        var wav = new ByteArrayOutputStream();
        try (var audio = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize())) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav);
        }
        return new VoiceNote(wav.toByteArray(), (pcm.length + BYTES_PER_SECOND - 1) / BYTES_PER_SECOND);
    }

    static void play(byte[] data) throws IOException, LineUnavailableException, UnsupportedAudioFileException {
        try (AudioInputStream audio = openAudio(data); SourceDataLine output = AudioSystem.getSourceDataLine(audio.getFormat())) {
            output.open(audio.getFormat());
            output.start();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = audio.read(buffer)) > 0) {
                output.write(buffer, 0, count);
            }
            output.drain();
        }
    }

    private static AudioInputStream openAudio(byte[] data) throws IOException, UnsupportedAudioFileException {
        var input = new ByteArrayInputStream(data);
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            return AudioSystem.getAudioInputStream(input);
        }
        // Voice notes from previous clients contain raw 8-bit samples.
        return new AudioInputStream(input, new AudioFormat(8_000f, 8, 1, true, false), data.length);
    }
}
