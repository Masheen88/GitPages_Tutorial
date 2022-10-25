$("#dropdownSection1").css("display", "none");

let tutorial1 = "#tutorial1";
let tutorial1Dropdown = "#dropdownSection1";
showDropdown(tutorial1, tutorial1Dropdown);
let tutorial2 = "#tutorial2";
let tutorial2Dropdown = "#dropdownSection2";
showDropdown(tutorial2, tutorial2Dropdown);

function showDropdown(tutorialID, tutorialDropdownID) {
  console.log("Showing dropdown ID:", tutorialID, tutorialDropdownID);

  $(`${tutorialID}`).click(function () {
    $(`${tutorialDropdownID}`).toggleClass("show");

    if ($(`${tutorialDropdownID}`).hasClass("show")) {
      $(`${tutorialDropdownID}`).fadeIn(900);
      $(`${tutorialDropdownID}`).css("display", "block");
    } else {
      $(`${tutorialDropdownID}`).fadeOut(800);
      setTimeout(function () {
        $(`${tutorialDropdownID}`).css("display", "none");
      }, 900);
    }
    //remove the click
  });
}

function getOS() {
  var userAgent = navigator.userAgent || navigator.vendor || window.opera;

  if (/android/i.test(userAgent)) {
    console.log("Android");
    return "Android";
  }

  // iOS
  if (/iPad|iPhone|iPod/.test(userAgent) && !window.MSStream) {
    console.log("iOS");
    return "iOS";
  }

  // Mac OS
  if (/Mac OS/.test(userAgent)) {
    console.log("MacOS");
    return "MacOS";
  }

  // Windows OS
  if (/Windows/.test(userAgent)) {
    console.log("Windows");
    return "Windows";
  }

  console.log("Unknown OS");
  return "unknown";
}

// let OSVersion = getOS();

if (OSVersion == "Windows") {
  $(".MacOS").css("display", "none");
  $("#youLikeMacs").css("display", "none");
}
if (OSVersion == "iOS" || OSVersion == "MacOS") {
  $(".tutorial").css("display", "none");
  $("#tutorial2").css("display", "block");
  $("#youLikePCs").css("display", "none");
}
