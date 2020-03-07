package com.jmal.juc.sream;

import lombok.Data;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class StreamDemo {

    /**
     * 按条件用户筛选：
     * 1、id 为偶数
     * 2、年龄大于24
     * 3、用户名大写   映射
     * 4、用户名倒排序
     * 5、输出一个用户
     *
     * 请你只用一行代码完成！
     */
    public static void main(String[] args) {
        User u1 = new User(1,"a",23);
        User u2 = new User(2,"b",24);
        User u3 = new User(3,"c",22);
        User u4 = new User(4,"d",28);
        User u5 = new User(6,"e",26);

        // 存储
        List<User> users = Arrays.asList(u1, u2, u3, u4, u5);
        // 计算等操作交给流
        // forEach(消费者类型接口)
        users.stream()
                .filter(u-> u.getId()%2==0)
                .filter(u-> u.getAge()>24)
                .map(u-> u.getName().toUpperCase())
                .sorted(Comparator.reverseOrder())
                .limit(1)
                .forEach(System.out::println);

        // 在JDK1.5 的时候，枚举：反射、注解、泛型
        // 在JDK1.8 的时候  函数式接口、Stream流式计算、lambda表达式、链式编程！
        // 无论何时，都还需要掌握一个东西叫 JVM；
        // JVM: 你会了这个技术不会觉得你恨厉害！
    }

}

@Data
class User {
    User(int id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }
    private int id;
    private String name;
    private int age;
}
