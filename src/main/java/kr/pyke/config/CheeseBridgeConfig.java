package kr.pyke.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import kr.pyke.CheeseBridge;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.Path;

public class CheeseBridgeConfig {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("cheese_bridge.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static ConfigInstance DATA = new ConfigInstance();

    public static class ConfigInstance {
        public ChzzkConfig chzzk = new ChzzkConfig();
        public SoopConfig soop = new SoopConfig();
    }

    public static class ChzzkConfig {
        public String clientID = "YOUR_CLIENT_ID";
        public String clientSecret = "YOUR_CLIENT_SECRET";
    }

    public static class SoopConfig {
        public String clientID = "YOUR_CLIENT_ID";
        public String clientSecret = "YOUR_CLIENT_SECRET";
    }

    public static void loadConfiguration() {
        File configFile = CONFIG_PATH.toFile();

        if (!configFile.exists()) {
            CheeseBridge.LOGGER.info("[CONFIG] 설정 파일이 없어 새로 생성합니다: {}", CONFIG_PATH.getFileName());
            DATA = new ConfigInstance();
            saveConfiguration();
        }
        else {
            try (FileReader reader = new FileReader(configFile)) {
                ConfigInstance loadedData = GSON.fromJson(reader, ConfigInstance.class);

                if (loadedData != null) {
                    DATA = loadedData;
                    CheeseBridge.LOGGER.info("[CONFIG] 성공적으로 설정을 로드했습니다.");
                }
                else {
                    DATA = new ConfigInstance();
                    CheeseBridge.LOGGER.warn("[CONFIG] 파일 내용이 비어있어 기본값으로 초기화합니다.");
                }
            }
            catch (Exception e) {
                CheeseBridge.LOGGER.error("[CONFIG] 설정 로드 중 오류 발생: ", e);
                DATA = new ConfigInstance();
            }
        }
    }

    public static void saveConfiguration() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(DATA, writer);
            CheeseBridge.LOGGER.info("[CONFIG] 설정을 성공적으로 저장했습니다.");
        }
        catch (IOException e) { CheeseBridge.LOGGER.error("[CONFIG] 설정 저장 실패: ", e); }
    }
}