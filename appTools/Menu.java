package appTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Menu {
    private String title;
    private List<MenuItem> items;

    public Menu(String title) {
        this.title = title;
        this.items = new ArrayList<>();
    }

    public void add(String name, Runnable action) {
        items.add(new MenuItem(name, action));
    }

    public void showAndSelect(BufferedReader br) {
        while (true) {
            System.out.println("\n" + title);
            for (int i = 0; i < items.size(); i++) {
                System.out.println((i + 1) + ". " + items.get(i).getName());
            }
            System.out.println("0. Exit");

            System.out.print("Enter your choice: ");
            try {
                String input = br.readLine();
                int choice = Integer.parseInt(input);

                if (choice == 0) {
                    break;
                } else if (choice > 0 && choice <= items.size()) {
                    items.get(choice - 1).getAction().run();
                } else {
                    System.out.println("Invalid choice. Please try again.");
                }
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    private static class MenuItem {
        private String name;
        private Runnable action;

        public MenuItem(String name, Runnable action) {
            this.name = name;
            this.action = action;
        }

        public String getName() {
            return name;
        }

        public Runnable getAction() {
            return action;
        }
    }
}
