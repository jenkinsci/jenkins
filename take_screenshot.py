import time
from selenium import webdriver
from selenium.webdriver.chrome import ChromeOptions

def take_screenshot(url, filename):
  # Create a Chrome driver instance.
  options = ChromeOptions()
  options.headless = True
  driver = webdriver.Chrome(options=options)

  # Go to the specified URL.
  driver.get(url)

  # Take a screenshot of the page.
  driver.save_screenshot(filename)

  # Close the driver.
  driver.quit()

if __name__ == "__main__":
  # Get the URL and filename from the user.
  url = input("Enter the URL: ")
  filename = input("Enter the filename: ")

  # Take the screenshot.
  take_screenshot(url, filename)

  # Print a success message.
  print("Screenshot taken successfully!")
