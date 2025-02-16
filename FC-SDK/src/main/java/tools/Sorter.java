package tools;

public class Sorter {

    public static void main(String[] args) {
        int[] arr = {10, 7, 8, 9, 1, 5};
        int n = arr.length;

        System.out.println("排序前的数组:");
        printArray(arr);

        quickSort(arr, 0, n - 1);

        System.out.println("排序后的数组:");
        printArray(arr);

        String[] strings = {"apple", "banana", "kiwi", "orange", "grape", "pear"};
        int length = strings.length;

        System.out.println("排序前的数组:");
        printArray(strings);

        quickSort(strings, 0, length - 1);

        System.out.println("排序后的数组:");
        printArray(strings);
    }

    // 快速排序函数
    public static void quickSort(int[] arr, int low, int high) {
        if (low < high) {
            // pi 是分区索引，arr[pi] 已经排好序
            int pi = partition(arr, low, high);

            // 递归排序左半部分和右半部分
            quickSort(arr, low, pi - 1);
            quickSort(arr, pi + 1, high);
        }
    }

    // 分区函数
    public static int partition(int[] arr, int low, int high) {
        int pivot = arr[high]; // 选择最后一个元素作为基准
        int i = (low - 1); // 较小元素的索引

        for (int j = low; j < high; j++) {
            // 如果当前元素小于或等于基准
            if (arr[j] <= pivot) {
                i++;

                // 交换 arr[i] 和 arr[j]
                int temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }
        }

        // 交换 arr[i+1] 和 arr[high] (基准)
        int temp = arr[i + 1];
        arr[i + 1] = arr[high];
        arr[high] = temp;

        return i + 1;
    }

    // 打印数组的函数
    public static void printArray(int[] arr) {
        for (int i : arr) {
            System.out.print(i + " ");
        }
        System.out.println();
    }

    // 快速排序函数
    public static void quickSort(String[] arr, int low, int high) {
        if (low < high) {
            // pi 是分区索引，arr[pi] 已经排好序
            int pi = partition(arr, low, high);

            // 递归排序左半部分和右半部分
            quickSort(arr, low, pi - 1);
            quickSort(arr, pi + 1, high);
        }
    }

    // 分区函数
    public static int partition(String[] arr, int low, int high) {
        String pivot = arr[high]; // 选择最后一个元素作为基准
        int i = (low - 1); // 较小元素的索引

        for (int j = low; j < high; j++) {
            // 如果当前字符串小于或等于基准字符串
            if (arr[j].compareTo(pivot) <= 0) {
                i++;

                // 交换 arr[i] 和 arr[j]
                String temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }
        }

        // 交换 arr[i+1] 和 arr[high] (基准)
        String temp = arr[i + 1];
        arr[i + 1] = arr[high];
        arr[high] = temp;

        return i + 1;
    }

    // 打印数组的函数
    public static void printArray(String[] arr) {
        for (String s : arr) {
            System.out.print(s + " ");
        }
        System.out.println();
    }
}
