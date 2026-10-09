#include "lifehash.hpp"
#include "color.hpp"
#include "hsb-color.hpp"
#include "numeric.hpp"

#include <cmath>
#include <iostream>
#include <string>

static void numeric_cases() {
    std::cout << std::hexfloat;
    for (auto rgb : {std::vector<int>{41, 115, 205}, {0, 128, 255}}) {
        auto color = LifeHash::Color::from_uint8_values(rgb[0], rgb[1], rgb[2]);
        std::cout << "palette " << rgb[0] << " " << rgb[1] << " " << rgb[2]
                  << " " << color.r << " " << color.g << " " << color.b << "\n";
    }
    for (auto color : {LifeHash::Color(0.1, 0.2, 0.3), LifeHash::Color(1, 1, 1), LifeHash::Color(0, 0, 0)}) {
        std::cout << "luminance " << color.r << " " << color.g << " " << color.b
                  << " " << color.luminance() << "\n";
    }
    for (auto pair : {std::vector<double>{std::nextafter(1.0, 0.0), 1.0}, {0.99999994, 1.0},
                      {-0.25, 1.0}, {0.23, 0.1}, {-0.23, 0.1}, {1.25, 1.0}}) {
        std::cout << "modulo " << pair[0] << " " << pair[1]
                  << " " << LifeHash::modulo(pair[0], pair[1]) << "\n";
    }
    // After modulo and scaling, float argument conversion rounds h up to 5 before floorf.
    double hue = -1.0 / 6.0;
    auto color = LifeHash::HSBColor(hue).color();
    std::cout << "hsb " << hue << " " << color.r << " " << color.g << " " << color.b << "\n";
}

// The wire format deliberately includes the API entry point and image options.
int main(int argc, char** argv) {
    if (argc == 2 && std::string(argv[1]) == "--numeric") {
        numeric_cases();
        return 0;
    }
    size_t id, version, module_size;
    bool alpha;
    std::string mode, hex;
    while (std::cin >> id >> mode >> version >> module_size >> alpha >> hex) {
        auto data = LifeHash::hex_to_data(hex == "-" ? "" : hex);
        auto v = static_cast<LifeHash::Version>(version);
        LifeHash::Image image;
        if (mode == "digest") {
            image = LifeHash::make_from_digest(data, v, module_size, alpha);
        } else if (mode == "utf8") {
            image = LifeHash::make_from_utf8(std::string(data.begin(), data.end()), v, module_size, alpha);
        } else {
            image = LifeHash::make_from_data(data, v, module_size, alpha);
        }
        std::cout << id << " " << image.width << " " << image.height << " "
                  << LifeHash::data_to_hex(image.colors) << "\n";
    }
    return std::cin.eof() ? 0 : 1;
}
