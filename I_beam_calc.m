%define the geometry of the I-beam cross section
Wtop = 0.2; %width of the top flange in meters
Ttop = 0.03; %height of the top flange in meters
Tweb = 0.025; %width of the web in meters
Hweb = 0.250; %height of the web in meters
Tbot = 0.03; %height of the bottom flange in meters
Wbot = 0.15; %width of the bottom flange in meters

%define the internal loading with specified test values
M = 50000; %internal bending moment in Newton-meters
V = 25000; %internal shear force in Newtons

%define the cross-sectional area of the top flange, web, and bottom flange
A_top = Wtop * Ttop;
A_web = Tweb * Hweb;
A_bot = Wbot * Tbot;

%compute the centroid of the cross section
y_top = Hweb + Ttop/2;
y_bot = Tbot/2;
y_web = Hweb/2;

A_total = A_top + A_web + A_bot;
y_centroid = (A_top * y_top + A_web * y_web + A_bot * y_bot) / A_total;

%compute the moment of inertia (I) of the cross section
I_top = (Wtop * Ttop^3) / 12 + A_top * (y_top - y_centroid)^2;
I_web = (Tweb * Hweb^3) / 12 + A_web * (y_web - y_centroid)^2;
I_bot = (Wbot * Tbot^3) / 12 + A_bot * (y_bot - y_centroid)^2;

I = I_top + I_web + I_bot;

%define the bending stress using flexure formula
y = linspace(-Tbot, Hweb + Ttop, 100);
bending_stress = -M .* (y - y_centroid) ./ I;

%define the shear stress distribution
Q = @(y) 0.5 * Wtop * Ttop * (Hweb + Ttop - y) + 0.5 * Tweb * (y - Ttop) * (y - Ttop) / Hweb * (Wtop + Wbot) / 2;
shear_stress = arrayfun(@(y) V * Q(y) / (I * Tweb), y);

%plot the cross section
figure;
hold on;
rectangle('Position', [-Wtop/2, Hweb, Wtop, Ttop], 'FaceColor', 'r');
rectangle('Position', [-Tweb/2, 0, Tweb, Hweb], 'FaceColor', 'g');
rectangle('Position', [-Wbot/2, -Tbot, Wbot, Tbot], 'FaceColor', 'b');
plot([-Wtop/2, Wtop/2], [Hweb, Hweb], 'k');
plot([-Tweb/2, Tweb/2], [0, 0], 'k');
plot([-Wbot/2, Wbot/2], [-Tbot, -Tbot], 'k');
title('Cross Section of I-Beam');
axis equal;
hold off;

%bending stress plot
figure;
plot(bending_stress, y);
title('Bending Stress Diagram');
xlabel('Bending Stress (Pa)');
ylabel('Position (y) along the cross-section (meters)');
grid on;

%shear stress plot
figure;
plot(shear_stress, y);
title('Shear Stress Diagram');
xlabel('Shear Stress (Pa)');
ylabel('Position (y) along the cross-section (meters)');
grid on;
