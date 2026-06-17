import java.lang.reflect.Method;

public class FindMethods {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.client.renderer.entity.EntityRenderer");
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().contains("renderNameTag") || m.getName().contains("render")) {
                System.out.println(m.getName() + " : " + m.toString());
            }
        }
    }
}
