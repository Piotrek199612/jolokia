package org.jolokia.jmx;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Date;

import javax.management.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 26.01.13
 */
public class JsonDymamicMBeanImplTest {

    private JolokiaMBeanServer server;
    private MBeanServer        platformServer;
    private ObjectName         testName;
    private ObjectName         userManagerName;

    @BeforeClass
    public void setup() {
        server = new JolokiaMBeanServer();
        platformServer = ManagementFactory.getPlatformMBeanServer();
    }

    @BeforeMethod
    public void registerBeans() throws Exception {
        testName = on("test:type=json");
        userManagerName = on("test:type=mxbean");
        register(testName, new Testing());
        register(userManagerName, new UserTestManager());
    }

    @AfterMethod
    public void unregisterBeans() throws Exception {
        unregister(testName);
        unregister(userManagerName);
    }

    @Test
    public void getAttribute() throws Exception {
        assertEquals(platformServer.getAttribute(testName,"Chili"),"jolokia");
        String user = (String) platformServer.getAttribute(testName,"User");
        JSONObject userJ = (JSONObject) toJSON(user);
        assertEquals(userJ.get("firstName"),"Hans");
        assertEquals(userJ.get("lastName"),"Kalb");

    }

    private Object toJSON(String string) throws ParseException {
        return new JSONParser().parse(string);
    }

    @Test
    public void setAttribute() throws Exception {
        platformServer.setAttribute(testName,new Attribute("Chili","fatalii"));
        assertEquals(platformServer.getAttribute(testName, "Chili"), "fatalii");

        platformServer.setAttribute(testName,new Attribute("Numbers","8,15"));
        String nums = (String) platformServer.getAttribute(testName,"Numbers");
        JSONArray numsJ = (JSONArray) toJSON(nums);
        assertEquals(numsJ.get(0),8L);
        assertEquals(numsJ.get(1),15L);
        assertEquals(numsJ.size(), 2);
    }

    @Test
    public void exec() throws Exception {
        String res = (String) platformServer.invoke(testName,"lookup",new Object[] { "Bumbes", "Eins, Zwei" }, new String[] { "java.lang.String", "java.lang.String" });
        JSONObject user = (JSONObject) toJSON(res);
        assertEquals(user.get("firstName"),"Hans");
        assertEquals(user.get("lastName"),"Kalb");

        Date date = new Date();
        long millis = (Long) platformServer.invoke(testName,"epoch", new Object[] { date.getTime() + "" }, new String[] { "java.lang.String"});
        assertEquals(date.getTime(),millis);

        char c = (Character) platformServer.invoke(testName,"charTest", new Object[] { 'y' }, new String[] { Character.class.getName() });
        assertEquals(c,'y');
    }

    @Test
    public void setGetAttributes() throws Exception {
        AttributeList attrList = new AttributeList(Arrays.asList(
                new Attribute("Chili","aji"),
                new Attribute("Numbers","16,11,68")));
        platformServer.setAttributes(testName,attrList);

        AttributeList ret =  platformServer.getAttributes(testName,new String[] { "Chili", "Numbers" });
        Attribute chili = (Attribute) ret.get(0);
        Attribute num = (Attribute) ret.get(1);
        assertEquals(chili.getValue(),"aji");

        JSONArray numsJ = (JSONArray) toJSON((String) num.getValue());
        assertEquals(numsJ.get(0),16L);
        assertEquals(numsJ.get(1),11L);
        assertEquals(numsJ.get(2),68L);
        assertEquals(numsJ.size(), 3);

        assertTrue(platformServer.getAttributes(testName,new String[0]).size() == 0);

    }


    @Test(expectedExceptions = RuntimeMBeanException.class, expectedExceptionsMessageRegExp = ".*convert.*")
    public void unconvertableArgument() throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, InvalidAttributeValueException {
        User other = new User("Max","Morlock");
        platformServer.setAttribute(testName, new Attribute("User", other.toJSONString()));
    }

    @Test
    public void openMBean() throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, InvalidAttributeValueException, ParseException {
        platformServer.setAttribute(userManagerName,new Attribute("User","{\"firstName\": \"Bumbes\", \"lastName\": \"Schmidt\"}"));
        String user = (String) platformServer.getAttribute(userManagerName,"User");
        JSONObject userJ = (JSONObject) toJSON(user);
        assertEquals(userJ.get("firstName"),"Bumbes");
        assertEquals(userJ.get("lastName"),"Schmidt");

        user = (String) platformServer.invoke(userManagerName,"lookup",
                              new Object[] {
                                      "Schmidt",
                                      "[{\"firstName\": \"Mama\", \"lastName\": \"Schmidt\"}," +
                                       "{\"firstName\": \"Papa\", \"lastName\": \"Schmidt\"}]"},
                              new String[] { String.class.getName(), String.class.getName() });
        userJ = (JSONObject) toJSON(user);
        assertEquals(userJ.get("firstName"),"Bumbes");
        assertEquals(userJ.get("lastName"), "Schmidt");
    }


    @AfterClass
    public void teardown() {
    }

    private ObjectName on(String name) throws MalformedObjectNameException {
        return new ObjectName(name);

    }
    private JsonDynamicMBeanImpl register(ObjectName oName, Object bean) throws Exception {
        server.registerMBean(bean, oName);

        JsonDynamicMBeanImpl jsonMBean = new JsonDynamicMBeanImpl(server,oName,server.getMBeanInfo(oName));
        platformServer.registerMBean(jsonMBean,oName);

        return jsonMBean;
    }

    private void unregister(ObjectName oName) throws Exception {
        server.unregisterMBean(oName);
        platformServer.unregisterMBean(oName);
    }


    // ===============================================================================
    // Test MBean

    public interface UserTestManagerMXBean {
        public void setUser(User user);

        public User getUser();

        public User lookup(String name, User[] parents);
    }

    public class UserTestManager implements UserTestManagerMXBean {
        User user = new User("Hans", "Kalb");

        public void setUser(User user) {
            this.user = user;
        }

        public User getUser() {
            return user;
        }

        public User lookup(String name, User[] parents) {
            return user;
        }
    }

    public interface TestingMBean {
        public String getChili();

        public void setChili(String name);

        public int[] getNumbers();
        public void setNumbers(int[] numbers);
        public User getUser();

        public User lookup(String name, String[] parents);
        public long epoch(Date date);

        public char charTest(Character c);
    }

    public static class User {
        private String firstName, lastName;

        public User() {
        }

        public User(String pFirstName, String pLastName) {
            firstName = pFirstName;
            lastName = pLastName;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setFirstName(String pFirstName) {
            firstName = pFirstName;
        }

        public void setLastName(String pLastName) {
            lastName = pLastName;
        }

        public String toJSONString() {
            return "{\"firstName\": \"" + firstName + "\", \"lastName\" : \"" + lastName + "\"}";
        }
    }

    public static class Testing implements TestingMBean {
        String chili = "jolokia";
        int[] numbers = new int[] { 47, 11};
        User user = new User("Hans", "Kalb");
        public String getChili() {
            return chili;
        }

        public void setChili(String name) {
            chili = name;
        }

        public int[] getNumbers() {
            return numbers;
        }

        public void setNumbers(int[] numbers) {
            this.numbers = numbers;
        }

        public User lookup(String name, String[] parents) {
            return user;
        }

        public long epoch(Date date) {
            return date.getTime();
        }

        public char charTest(Character c) {
            return c;
        }

        public User getUser() {
            return user;
        }
    }
}
